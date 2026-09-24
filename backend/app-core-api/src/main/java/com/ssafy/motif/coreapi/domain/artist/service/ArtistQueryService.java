package com.ssafy.motif.coreapi.domain.artist.service;

import com.ssafy.motif.common.error.CommonErrorCode;
import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.common.response.CursorResponse;
import com.ssafy.motif.common.response.ListResponse;
import com.ssafy.motif.coreapi.domain.artist.ArtistErrorCode;
import com.ssafy.motif.coreapi.domain.artist.dto.ArtistCardResponse;
import com.ssafy.motif.coreapi.domain.artist.dto.ArtistChallengeResponse;
import com.ssafy.motif.coreapi.domain.artist.dto.ArtistDetailResponse;
import com.ssafy.motif.coreapi.domain.artist.dto.ArtistNoticeResponse;
import com.ssafy.motif.coreapi.domain.artist.dto.ArtistSearchResponse;
import com.ssafy.motif.coreapi.global.media.PublicMediaUrlResolver;
import com.ssafy.motif.coreapi.global.response.Cursors;
import com.ssafy.motif.domain.core.artist.ArtistCardRow;
import com.ssafy.motif.domain.core.artist.ArtistCount;
import com.ssafy.motif.domain.core.artist.ArtistListRow;
import com.ssafy.motif.domain.core.artist.ArtistRepository;
import com.ssafy.motif.domain.core.artist.ArtistRepository.ArtistSearchRow;
import com.ssafy.motif.domain.core.artist.Subscription;
import com.ssafy.motif.domain.core.artist.SubscriptionRepository;
import com.ssafy.motif.domain.core.challenge.Challenge;
import com.ssafy.motif.domain.core.challenge.ChallengeRepository;
import com.ssafy.motif.domain.core.notice.Notice;
import com.ssafy.motif.domain.core.notice.NoticeImage;
import com.ssafy.motif.domain.core.notice.NoticeImageRepository;
import com.ssafy.motif.domain.core.notice.NoticeRepository;
import com.ssafy.motif.domain.core.pass.MotifPassRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 팬 앱 아티스트 조회 (명세 §5.3 · §5.4 · §5.5 · §5.10) — S15P21D104-67 · 76 · 79 · 34.
 *
 * <p><b>어드민 §3.5 와 나란한 별개 서비스다.</b> 같은 테이블을 읽지만 계약이 다르다 —
 * 어드민은 offset 페이지(§1.5)에 삭제분 포함 여부를 고르고 운영 칼럼(소속사·
 * {@code motifBgImageKey})까지 보여 주는 반면, 여기는 커서(§1.9)에 §1.8 비노출이
 * 무조건 걸리고 "내" 상태({@code isSubscribed}·{@code pass}·{@code isLiked})가 붙는다.
 * 한 서비스에 {@code isAdmin} 같은 분기를 두면 비노출 규칙이 플래그 하나로 꺼진다.
 *
 * <p>읽기 전용 트랜잭션이다 — 쓰기 경로가 없다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ArtistQueryService {

    /** §1.9 — {@code size} 기본 20·최대 100. 기본값은 컨트롤러가 갖는다. */
    public static final int MAX_SIZE = 100;

    private final ArtistRepository artistRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final ChallengeRepository challengeRepository;
    private final NoticeRepository noticeRepository;
    private final NoticeImageRepository noticeImageRepository;
    private final MotifPassRepository motifPassRepository;
    private final PublicMediaUrlResolver mediaUrlResolver;

    /**
     * §5.10 전체 아티스트 목록 — <b>구독자 수 내림차순 전량</b>, 비페이지.
     *
     * <p><b>§5.1 랭킹과 다른 API 다.</b> 저쪽은 배치가 만든 Redis 스냅샷이 1차 출처이고
     * 정렬이 챌린지 반응 점수라 구독자 수순이 아니다 — 구독은 점수에 들어가지 않는다
     * (§5.6). {@code size} 만 키워 재사용하면 화면이 요구하는 순서가 나오지 않는다.
     *
     * <p><b>정렬은 DB 가 한다</b>({@link ArtistRepository#findCardsBySubscriberCountDesc}).
     * 여기서 다시 세우면 정렬 기준이 두 곳에 생긴다.
     *
     * <p><b>DB 조회는 2회로 고정이다</b> — 목록 1회, 구독 여부 1회. 항목마다 구독을 물으면
     * 그대로 N+1 이고, 아티스트가 수백 규모라 그 차이가 그대로 응답 시간이 된다.
     */
    public ListResponse<ArtistCardResponse> list(Integer userSeq) {
        List<ArtistCardRow> rows = artistRepository.findCardsBySubscriberCountDesc();
        if (rows.isEmpty()) {
            // 빈 컬렉션을 in 절에 넘기지 않는다 — findSubscribedArtistSeqs 는 네이티브
            // 쿼리라 `in ()` 가 문법 오류로 터진다
            return ListResponse.of(List.of());
        }

        List<Integer> artistSeqs = rows.stream().map(ArtistCardRow::artistSeq).toList();
        Set<Integer> subscribed = Set.copyOf(
                subscriptionRepository.findSubscribedArtistSeqs(userSeq, artistSeqs));

        return ListResponse.of(rows.stream()
                .map(row -> new ArtistCardResponse(
                        row.artistSeq(),
                        row.name(),
                        row.groupName(),
                        mediaUrlResolver.resolveProfile(row.profileImageKey()),
                        subscribed.contains(row.artistSeq())))
                .toList());
    }

    /**
     * §5.2 아티스트 검색 — <b>자동완성 겸용</b>. 타이핑 중이든 엔터 실행이든 같은 API 다.
     *
     * <p><b>이름이 아니라 별칭을 훑는다</b>(ADR-0014). 언어별 별칭을 저장해 "Karina" 로 치든
     * "카리나" 로 치든 같은 아티스트가 나오게 한다. 응답의 {@code name} 은 그래도
     * {@code users.nickname} 이다 — 별칭은 찾는 데만 쓴다.
     *
     * <p><b>DB 조회는 2회로 고정이다</b> — 검색 1회, 구독 여부 1회({@link #list} 와 같은 방식).
     * 항목마다 구독을 물으면 그대로 N+1 이고, 타이핑마다 불리는 API 라 그 차이가 그대로
     * 체감 지연이 된다.
     *
     * <p>페이지가 없다. 상위 {@code size} 건만 주고 그 이상은 검색어를 좁히는 것이 자연스럽다.
     */
    public ListResponse<ArtistSearchResponse> search(Integer userSeq, String q, int size) {
        String keyword = q == null ? "" : q.trim();
        if (keyword.isEmpty()) {
            // 빈 검색어를 막는 유일한 지점이다 — 컨트롤러 파라미터 제약(@NotBlank)은
            // @Validated 없이는 동작하지 않고 이 레포에 그 선례가 없다. 그대로 내려가면
            // ilike '%%' 가 되어 §5.10 전량 조회가 된다
            throw new BusinessException(CommonErrorCode.INVALID_PARAMETER,
                    "검색어를 입력하세요.");
        }

        List<ArtistSearchRow> rows = artistRepository.searchByAlias(
                keyword, contains(keyword), prefix(keyword), Math.min(size, MAX_SIZE));
        if (rows.isEmpty()) {
            // 빈 컬렉션을 in 절에 넘기지 않는다 — findSubscribedArtistSeqs 는 네이티브라
            // `in ()` 가 문법 오류로 터진다(list() 와 같은 이유)
            return ListResponse.of(List.of());
        }

        List<Integer> artistSeqs = rows.stream().map(ArtistSearchRow::getArtistSeq).toList();
        Set<Integer> subscribed = Set.copyOf(
                subscriptionRepository.findSubscribedArtistSeqs(userSeq, artistSeqs));

        return ListResponse.of(rows.stream()
                .map(row -> new ArtistSearchResponse(
                        row.getArtistSeq(),
                        row.getName(),
                        row.getGroupName(),
                        mediaUrlResolver.resolveProfile(row.getProfileImageKey()),
                        subscribed.contains(row.getArtistSeq())))
                .toList());
    }

    /**
     * LIKE 메타문자를 막는다 — 사용자가 친 글자를 <b>패턴이 아니라 값으로</b> 찾는다.
     *
     * <p>검색어를 {@code '%' || q || '%'} 한가운데에 끼우므로, 이스케이프하지 않으면
     * {@code %} 한 글자가 모든 별칭에 걸려 <b>검색어와 무관한 상위 {@code size} 건</b>이
     * 나온다({@code _} 는 아무 글자 1개가 된다). Postgres {@code ilike} 의 기본 이스케이프
     * 문자가 {@code \} 라 별도 {@code escape} 절이 필요 없다.
     *
     * <p><b>{@code \} 를 먼저 치환한다.</b> 나중에 하면 앞서 넣은 이스케이프의
     * {@code \} 까지 다시 이스케이프해 버린다.
     */
    private static String escapeLike(String keyword) {
        return keyword.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    private static String contains(String keyword) {
        return "%" + escapeLike(keyword) + "%";
    }

    private static String prefix(String keyword) {
        return escapeLike(keyword) + "%";
    }

    /** §5.3 아티스트 상세. */
    public ArtistDetailResponse detail(Integer userSeq, Integer artistSeq) {
        ArtistListRow artist = requireArtist(artistSeq);

        Subscription subscription = subscriptionRepository
                .findByIdUserSeqAndIdArtistSeq(userSeq, artistSeq)
                .orElse(null);
        Instant passExpiry = motifPassRepository
                .findActivePassExpiry(userSeq, artistSeq)
                .orElse(null);

        return new ArtistDetailResponse(
                artist.artistSeq(),
                artist.name(),
                artist.groupName(),
                mediaUrlResolver.resolveProfile(artist.profileImageKey()),
                artist.bio(),
                artist.subscriberCount(),
                challengeCount(artistSeq),
                subscription != null,
                subscribedDays(subscription),
                ArtistDetailResponse.MotifPass.of(passExpiry),
                // ponytail: §1.7 보호 등급 URL 을 만들 수단이 아직 없어 빈 배열로 둔다.
                // ProtectedMediaUrlResolver(썸네일)·재생목록 리라이터(videoUrl)가 붙으면 여기서
                // motif_results 최신 5건을 읽어 채운다 — 만료·개수는 명세 §16 #23 미결.
                // 공개 URL 로 대신 내보내지 않는다: 유료·비공개 결과물이 무기명 링크로
                // 새는 쪽이 기능이 비는 쪽보다 훨씬 나쁘다.
                List.of());
    }

    /** §5.4 아티스트 챌린지 그리드 — 비페이지 전량, 최신순. */
    public ListResponse<ArtistChallengeResponse> challenges(Integer artistSeq) {
        requireArtist(artistSeq);

        return ListResponse.of(
                challengeRepository.findByArtistSeqAndDeletedFalseOrderByChallengeSeqDesc(artistSeq)
                        .stream()
                        .map(this::toChallengeItem)
                        .toList());
    }

    private ArtistChallengeResponse toChallengeItem(Challenge challenge) {
        return new ArtistChallengeResponse(
                challenge.getChallengeSeq(),
                mediaUrlResolver.resolve(challenge.getThumbnailKey()),
                challenge.getViewCount());
    }

    /**
     * §5.5 아티스트 공지 목록 — 커서, 최신순 고정.
     *
     * <p>{@code size + 1} 건을 읽어 넘치면 잘라내고 그 사실을 {@code hasNext} 로 쓴다 —
     * 총 개수를 세지 않는 것이 커서 방식의 요점이다(§1.9). 딱 {@code size} 건으로
     * 끊고 "다음이 있는지" 를 따로 세면 매 페이지마다 count 쿼리가 붙는다.
     */
    public CursorResponse<ArtistNoticeResponse> notices(Integer userSeq, Integer artistSeq,
                                                        String cursor, int size) {
        ArtistListRow artist = requireArtist(artistSeq);
        int limit = Math.clamp(size, 1, MAX_SIZE);
        Integer after = Cursors.decode(cursor);

        // 커서 유무로 메서드를 가른다 — null 을 쿼리 파라미터로 흘리지 않기 위해서다
        // (NoticeRepository javadoc: 타입 없는 null → bytea 추론 → 운영 500)
        PageRequest page = PageRequest.of(0, limit + 1);
        List<Notice> rows = after == null
                ? noticeRepository.findByArtistSeqAndDeletedFalseOrderByNoticeSeqDesc(artistSeq, page)
                : noticeRepository.findByArtistSeqAndDeletedFalseAndNoticeSeqLessThanOrderByNoticeSeqDesc(
                        artistSeq, after, page);

        boolean hasNext = rows.size() > limit;
        List<Notice> notices = hasNext ? rows.subList(0, limit) : rows;
        if (notices.isEmpty()) {
            return CursorResponse.forward(List.of(), null);
        }

        ArtistNoticeResponse.Artist writer = new ArtistNoticeResponse.Artist(
                artist.artistSeq(), artist.name(), mediaUrlResolver.resolveProfile(artist.profileImageKey()));
        List<Integer> noticeSeqs = notices.stream().map(Notice::getNoticeSeq).toList();
        Map<Integer, List<ArtistNoticeResponse.Image>> images = imagesByNotice(noticeSeqs);
        Set<Integer> liked = Set.copyOf(noticeRepository.findLikedNoticeSeqs(userSeq, noticeSeqs));

        List<ArtistNoticeResponse> list = notices.stream()
                .map(notice -> new ArtistNoticeResponse(
                        notice.getNoticeSeq(),
                        writer,
                        notice.getContent(),
                        images.getOrDefault(notice.getNoticeSeq(), List.of()),
                        notice.getLikeCount(),
                        notice.getCommentCount(),
                        liked.contains(notice.getNoticeSeq()),
                        notice.getRegDttm()))
                .toList();

        String nextCursor = hasNext ? Cursors.encode(noticeSeqs.getLast()) : null;
        return CursorResponse.forward(list, nextCursor);
    }

    /** 한 페이지 분 이미지를 한 번에 읽어 {@code noticeSeq} 로 묶는다 — 항목마다 읽으면 N+1. */
    private Map<Integer, List<ArtistNoticeResponse.Image>> imagesByNotice(List<Integer> noticeSeqs) {
        return noticeImageRepository.findByNoticeSeqInOrderByNoticeSeqAscDisplayOrderAsc(noticeSeqs)
                .stream()
                .collect(Collectors.groupingBy(
                        NoticeImage::getNoticeSeq,
                        Collectors.mapping(this::toImage, Collectors.toList())));
    }

    private ArtistNoticeResponse.Image toImage(NoticeImage image) {
        return new ArtistNoticeResponse.Image(
                mediaUrlResolver.resolve(image.getImageKey()), image.getDisplayOrder());
    }

    /**
     * §5.3·§5.4·§5.5 공통 404 — 미존재·삭제 아티스트는 {@code ARTIST_NOT_FOUND} 다.
     *
     * <p>{@code findRow} 는 <b>삭제분도 준다</b>(어드민 상세가 그것을 필요로 한다) —
     * 팬 앱에서는 §1.8 대로 없는 것으로 답해야 하므로 여기서 한 번 더 거른다.
     */
    private ArtistListRow requireArtist(Integer artistSeq) {
        return artistRepository.findRow(artistSeq)
                .filter(row -> !row.deleted())
                .orElseThrow(() -> new BusinessException(
                        ArtistErrorCode.ARTIST_NOT_FOUND, "artistSeq: " + artistSeq));
    }

    /** 집계는 아티스트별 한 행이라, 없으면 0 건이라는 뜻이다. */
    private long challengeCount(Integer artistSeq) {
        return challengeRepository.countByArtists(List.of(artistSeq)).stream()
                .mapToLong(ArtistCount::count)
                .findFirst()
                .orElse(0L);
    }

    /**
     * 구독 경과일 — 미구독이면 {@code null} (§5.3).
     *
     * <p>{@code ChronoUnit.DAYS} 는 24시간 단위 내림이라 "구독한 그날"은 0 이다 —
     * 화면의 "WITH n days" 와 맞는다(1일이 지나야 1).
     */
    private Long subscribedDays(Subscription subscription) {
        return subscription == null
                ? null
                : ChronoUnit.DAYS.between(subscription.getRegDttm(), Instant.now());
    }
}
