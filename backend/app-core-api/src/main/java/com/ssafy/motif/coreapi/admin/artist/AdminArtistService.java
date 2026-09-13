package com.ssafy.motif.coreapi.admin.artist;

import com.ssafy.motif.common.error.AdminErrorCode;
import com.ssafy.motif.common.error.CommonErrorCode;
import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.coreapi.admin.artist.dto.ArtistCreateRequest;
import com.ssafy.motif.coreapi.admin.artist.dto.ArtistCreateResponse;
import com.ssafy.motif.coreapi.admin.artist.dto.ArtistDetailResponse;
import com.ssafy.motif.coreapi.admin.artist.dto.ArtistSummaryResponse;
import com.ssafy.motif.coreapi.admin.artist.dto.ArtistUpdateRequest;
import com.ssafy.motif.coreapi.admin.audit.AdminAuditLogger;
import com.ssafy.motif.coreapi.admin.media.AdminMediaService;
import com.ssafy.motif.coreapi.admin.pass.AdminPassPriceService;
import com.ssafy.motif.coreapi.admin.security.AuthenticatedAdmin;
import com.ssafy.motif.coreapi.domain.artist.ArtistErrorCode;
import com.ssafy.motif.coreapi.domain.media.MediaErrorCode;
import com.ssafy.motif.coreapi.global.media.PublicMediaUrlResolver;
import com.ssafy.motif.coreapi.global.response.PageResponse;
import com.ssafy.motif.domain.core.artist.Artist;
import com.ssafy.motif.domain.core.artist.ArtistCount;
import com.ssafy.motif.domain.core.artist.ArtistGroup;
import com.ssafy.motif.domain.core.artist.ArtistGroupRepository;
import com.ssafy.motif.domain.core.artist.ArtistListRow;
import com.ssafy.motif.domain.core.artist.ArtistRepository;
import com.ssafy.motif.domain.core.artist.Company;
import com.ssafy.motif.domain.core.artist.CompanyRepository;
import com.ssafy.motif.domain.core.asset.MotifAssetRepository;
import com.ssafy.motif.domain.core.challenge.Challenge;
import com.ssafy.motif.domain.core.challenge.ChallengeRepository;
import com.ssafy.motif.domain.core.notice.NoticeRepository;
import com.ssafy.motif.domain.core.user.User;
import com.ssafy.motif.domain.core.user.UserRepository;
import com.ssafy.motif.domain.core.user.UserRole;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 아티스트 목록·등록·상세·수정 (어드민 명세 §3.5.1~§3.5.4) — S15P21D104-385.
 *
 * <p><b>{@code users(ARTIST)} 와 {@code artists} 를 한 트랜잭션에 만든다.</b>
 * {@code artists} 에는 이름 칸이 없고({@code users.nickname} 이 이름이다) FK·
 * {@code uq_artists_user} 로 묶여 있어, 둘 중 하나만 남으면 이름 없는 아티스트이거나
 * 아무 역할도 없는 ARTIST 유저가 된다. {@code user_accounts} 는 만들지 않는다 —
 * 아티스트는 로그인하지 않는다(회의 확정).
 *
 * <p><b>소속사는 그룹을 따라간다.</b> 그룹을 지정하면 {@code companySeq} 는 그 그룹의
 * 소속사와 같아야 하고(불일치 400), 그룹에 속한 아티스트의 소속사만 따로 바꾸는 요청은
 * 409 {@code ARTIST_COMPANY_LOCKED} 다(DB §9). 두 규칙은 같은 불변식의 앞뒤다 —
 * "그룹 소속 아티스트의 소속사 = 그룹 소속사".
 *
 * <p><b>이미지는 키만 받는다.</b> 업로드 API 를 따로 두지 않는다 — 화면이 §3.2 presign 으로
 * 브라우저 → S3 직행 PUT 을 끝내고 그 키를 폼에 실어 보낸다(§1.4 원칙 ①). 서버가 하는 일은
 * 존재 확인(HEAD)과 칼럼 저장뿐이다. 프로필은 {@code users.profile_image_key},
 * 모티프 배경은 {@code artists.motif_bg_image_key} 로 <b>테이블이 갈린다</b> —
 * 프로필은 모든 유저의 것이고 배경은 아티스트 전용이기 때문이다.
 *
 * <p>삭제(§3.5.5)는 여기 없다 — 에셋·가격 연쇄가 붙는 별도 티켓이다(S15P21D104-558).
 */
@Service
@RequiredArgsConstructor
public class AdminArtistService {

    /** §1.5 — 목록 size 기본 20·최대 100. */
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    private final ArtistRepository artistRepository;
    private final ArtistGroupRepository artistGroupRepository;
    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final ChallengeRepository challengeRepository;
    private final MotifAssetRepository motifAssetRepository;
    private final NoticeRepository noticeRepository;
    private final AdminPassPriceService adminPassPriceService;
    private final AdminMediaService adminMediaService;
    private final PublicMediaUrlResolver mediaUrlResolver;
    private final AdminAuditLogger auditLogger;

    /** §3.5.1 — 이름 부분 일치·그룹·소속사 필터, 최신 등록순. */
    @Transactional(readOnly = true)
    public PageResponse<ArtistSummaryResponse> list(String q, Integer groupSeq, Integer companySeq,
                                                    boolean includeDeleted, int page, int size) {
        Page<ArtistListRow> rows = artistRepository.search(
                blankToNull(q), groupSeq, companySeq, includeDeleted,
                PageRequest.of(Math.max(page, 0), clampSize(size)));

        List<Integer> artistSeqs = rows.getContent().stream().map(ArtistListRow::artistSeq).toList();
        Counts counts = countsOf(artistSeqs);
        return PageResponse.of(rows, row -> summary(row, counts));
    }

    /** §3.5.2 — {@code users} → {@code artists} 원자 생성. */
    @Transactional
    public ArtistCreateResponse create(AuthenticatedAdmin admin, ArtistCreateRequest request) {
        Organization org = resolveForCreate(request.groupSeq(), request.companySeq());
        requireUploaded(request.profileImageKey(), request.motifBgImageKey());

        User user = userRepository.save(User.createArtist(request.name().strip()));
        user.changeProfileImageKey(request.profileImageKey());
        Artist artist = artistRepository.save(Artist.register(
                user.getUserSeq(), org.groupSeq(), org.companySeq(),
                request.bio(), request.motifBgImageKey(), admin.userSeq()));

        auditLogger.write(admin, "CREATE_ARTIST", "ARTIST", artist.getArtistSeq(), AdminAuditLogger.RESULT_OK);
        return new ArtistCreateResponse(artist.getArtistSeq(), user.getUserSeq());
    }

    /** §3.5.3 — 목록 항목 + bio·배경·패스 가격·최신 챌린지 5건. */
    @Transactional(readOnly = true)
    public ArtistDetailResponse detail(int artistSeq) {
        ArtistListRow row = row(artistSeq);
        ArtistSummaryResponse summary = summary(row, countsOf(List.of(artistSeq)));

        List<ArtistDetailResponse.RecentChallenge> recent = challengeRepository
                .findTop5ByArtistSeqAndDeletedFalseOrderByChallengeSeqDesc(artistSeq).stream()
                .map(this::recentChallenge)
                .toList();

        return new ArtistDetailResponse(summary, row.bio(),
                mediaUrlResolver.resolve(row.motifBgImageKey()),
                adminPassPriceService.list(artistSeq).list(), recent);
    }

    /** §3.5.4 — 부분 수정. 이름은 {@code users.nickname}, 소속은 그룹 규칙을 탄다. */
    @Transactional
    public ArtistDetailResponse update(AuthenticatedAdmin admin, int artistSeq,
                                       ArtistUpdateRequest request) {
        Artist artist = artistRepository.findByArtistSeqAndDeletedFalse(artistSeq)
                .orElseThrow(() -> new BusinessException(ArtistErrorCode.ARTIST_NOT_FOUND,
                        "미존재·삭제 아티스트: artistSeq=" + artistSeq));

        requireUploaded(request.getProfileImageKey(), request.getMotifBgImageKey());

        if (request.getName() != null || request.getProfileImageKey() != null) {
            User user = userRepository.findById(artist.getUserSeq())
                    .orElseThrow(() -> new IllegalStateException(
                            "아티스트에 users 행이 없다 — FK 로 불가능해야 한다: artistSeq=" + artistSeq));
            if (request.getName() != null) {
                user.changeNickname(request.getName().strip());
            }
            user.changeProfileImageKey(request.getProfileImageKey());
            user.recordModifier(admin.userSeq());
        }
        artist.changeProfile(request.getBio(), request.getMotifBgImageKey());
        applyOrganization(artist, request);

        auditLogger.write(admin, "UPDATE_ARTIST", "ARTIST", artistSeq, AdminAuditLogger.RESULT_OK);
        artistRepository.flush();
        return detail(artistSeq);
    }

    // --- 소속 규칙 -----------------------------------------------------------

    /** 등록 시 그룹·소속사 짝 맞추기 (§3.5.2). */
    private Organization resolveForCreate(Integer groupSeq, Integer companySeq) {
        if (groupSeq == null) {
            if (companySeq == null) {
                throw new BusinessException(CommonErrorCode.INVALID_PARAMETER,
                        "솔로 아티스트는 companySeq 가 필수다");
            }
            return new Organization(null, company(companySeq).getCompanySeq());
        }
        ArtistGroup group = group(groupSeq);
        if (companySeq != null && !companySeq.equals(group.getCompanySeq())) {
            throw new BusinessException(CommonErrorCode.INVALID_PARAMETER,
                    "그룹 소속사와 불일치: groupSeq=%d groupCompanySeq=%d companySeq=%d"
                            .formatted(groupSeq, group.getCompanySeq(), companySeq),
                    Map.of("groupCompanySeq", group.getCompanySeq()));
        }
        return new Organization(group.getGroupSeq(), group.getCompanySeq());
    }

    /**
     * 수정 시 소속 변경 (§3.5.4).
     *
     * <p>세 갈래다 — ① {@code groupSeq} 키 생략 + {@code companySeq} 지정 = 소속사 단독
     * 변경. 그룹에 속해 있으면 409 다. ② {@code groupSeq: null} = 솔로 전환, 소속사는
     * 함께 온 {@code companySeq} 또는 현재 값. ③ {@code groupSeq} 지정 = 그룹 이동,
     * {@code companySeq} 는 그 그룹 소속사와 같아야 한다.
     */
    private void applyOrganization(Artist artist, ArtistUpdateRequest request) {
        Integer companySeq = request.getCompanySeq();

        if (!request.isGroupSeqPresent()) {
            if (companySeq == null || companySeq.equals(artist.getCompanySeq())) {
                return;
            }
            if (artist.getGroupSeq() != null) {
                throw new BusinessException(AdminErrorCode.ARTIST_COMPANY_LOCKED,
                        "그룹 소속 아티스트의 소속사 단독 변경: artistSeq=%d groupSeq=%d"
                                .formatted(artist.getArtistSeq(), artist.getGroupSeq()),
                        Map.of("groupSeq", artist.getGroupSeq()));
            }
            artist.changeOrganization(null, company(companySeq).getCompanySeq());
            return;
        }

        if (request.getGroupSeq() == null) {
            Integer target = companySeq == null ? artist.getCompanySeq() : company(companySeq).getCompanySeq();
            artist.changeOrganization(null, target);
            return;
        }

        Organization org = resolveForCreate(request.getGroupSeq(), companySeq);
        artist.changeOrganization(org.groupSeq(), org.companySeq());
    }

    private record Organization(Integer groupSeq, Integer companySeq) {
    }

    // --- 조회 조립 -----------------------------------------------------------

    /** 챌린지·에셋·공지 수를 페이지 단위로 한 번에 (행마다 세면 N+1 이다). */
    private Counts countsOf(List<Integer> artistSeqs) {
        if (artistSeqs.isEmpty()) {
            return new Counts(Map.of(), Map.of(), Map.of());
        }
        return new Counts(
                toMap(challengeRepository.countByArtists(artistSeqs)),
                toMap(motifAssetRepository.countByArtists(artistSeqs)),
                toMap(noticeRepository.countByArtists(artistSeqs)));
    }

    private record Counts(Map<Integer, Long> challenges, Map<Integer, Long> assets,
                          Map<Integer, Long> notices) {
    }

    private static Map<Integer, Long> toMap(List<ArtistCount> counts) {
        return counts.stream().collect(Collectors.toMap(ArtistCount::artistSeq, ArtistCount::count));
    }

    private ArtistSummaryResponse summary(ArtistListRow row, Counts counts) {
        return new ArtistSummaryResponse(
                row.artistSeq(), row.userSeq(), row.name(),
                row.groupSeq() == null ? null
                        : new ArtistSummaryResponse.GroupRef(row.groupSeq(), row.groupName()),
                new ArtistSummaryResponse.CompanyRef(row.companySeq(), row.companyName()),
                mediaUrlResolver.resolve(row.profileImageKey()),
                row.subscriberCount(),
                counts.challenges().getOrDefault(row.artistSeq(), 0L),
                counts.assets().getOrDefault(row.artistSeq(), 0L),
                counts.notices().getOrDefault(row.artistSeq(), 0L),
                row.deleted(), row.regDttm());
    }

    private ArtistDetailResponse.RecentChallenge recentChallenge(Challenge challenge) {
        return new ArtistDetailResponse.RecentChallenge(challenge.getChallengeSeq(),
                challenge.getDescription(), mediaUrlResolver.resolve(challenge.getThumbnailKey()));
    }

    private ArtistListRow row(int artistSeq) {
        return artistRepository.findRow(artistSeq)
                .orElseThrow(() -> new BusinessException(ArtistErrorCode.ARTIST_NOT_FOUND,
                        "미존재 아티스트: artistSeq=" + artistSeq));
    }

    // --- 참조 검증 -----------------------------------------------------------

    private ArtistGroup group(Integer groupSeq) {
        return artistGroupRepository.findByGroupSeqAndDeletedFalse(groupSeq)
                .orElseThrow(() -> new BusinessException(AdminErrorCode.GROUP_NOT_FOUND,
                        "미존재·삭제 그룹: groupSeq=" + groupSeq));
    }

    private Company company(Integer companySeq) {
        return companyRepository.findByCompanySeqAndDeletedFalse(companySeq)
                .orElseThrow(() -> new BusinessException(AdminErrorCode.COMPANY_NOT_FOUND,
                        "미존재·삭제 소속사: companySeq=" + companySeq));
    }

    /**
     * 이미지 키 존재 확인 (§1.4 검증 1항).
     *
     * <p>영상의 {@code MEDIA_NOT_UPLOADED} 가 아니라 {@code IMAGE_NOT_UPLOADED} 를 쓴다 —
     * §3.5 실패표가 그렇게 배정했고, 사용자 프로필 교체(API §4.4)와 같은 코드다.
     * 빠진 키는 한 건에서 멈추지 않고 <b>전부 모아</b> {@code data} 로 돌려준다.
     */
    private void requireUploaded(String... keys) {
        List<String> missing = Stream.of(keys)
                .filter(key -> key != null && !key.isBlank())
                .filter(key -> !adminMediaService.exists(key))
                .toList();
        if (!missing.isEmpty()) {
            throw new BusinessException(MediaErrorCode.IMAGE_NOT_UPLOADED,
                    "S3 에 없는 이미지 키: " + missing, Map.of("imageKeys", missing));
        }
    }

    private static int clampSize(int size) {
        return size <= 0 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
