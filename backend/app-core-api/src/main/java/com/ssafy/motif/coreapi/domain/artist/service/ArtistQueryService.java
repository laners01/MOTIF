package com.ssafy.motif.coreapi.domain.artist.service;

import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.common.response.ListResponse;
import com.ssafy.motif.coreapi.domain.artist.ArtistErrorCode;
import com.ssafy.motif.coreapi.domain.artist.dto.ArtistChallengeResponse;
import com.ssafy.motif.coreapi.domain.artist.dto.ArtistDetailResponse;
import com.ssafy.motif.coreapi.global.media.PublicMediaUrlResolver;
import com.ssafy.motif.domain.core.artist.ArtistCount;
import com.ssafy.motif.domain.core.artist.ArtistListRow;
import com.ssafy.motif.domain.core.artist.ArtistRepository;
import com.ssafy.motif.domain.core.artist.Subscription;
import com.ssafy.motif.domain.core.artist.SubscriptionRepository;
import com.ssafy.motif.domain.core.challenge.Challenge;
import com.ssafy.motif.domain.core.challenge.ChallengeRepository;
import com.ssafy.motif.domain.core.pass.MotifPassPriceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 팬 앱 아티스트 조회 (명세 §5.3 · §5.4) — S15P21D104-67 · 76.
 *
 * <p><b>어드민 §3.5 와 나란한 별개 서비스다.</b> 같은 테이블을 읽지만 계약이 다르다 —
 * 어드민은 삭제분 포함 여부를 고르고 운영 칼럼(소속사·{@code motifBgImageKey})까지
 * 보여 주는 반면, 여기는 §1.8 비노출이 무조건 걸리고 "내" 상태
 * ({@code isSubscribed}·{@code pass})가 응답에 붙는다. 한 서비스에 {@code isAdmin} 같은
 * 분기를 두면 비노출 규칙이 플래그 하나로 꺼진다.
 *
 * <p>읽기 전용 트랜잭션이다 — 쓰기 경로가 없다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ArtistQueryService {

    private final ArtistRepository artistRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final ChallengeRepository challengeRepository;
    private final MotifPassPriceRepository motifPassPriceRepository;
    private final PublicMediaUrlResolver mediaUrlResolver;

    /** §5.3 아티스트 상세. */
    public ArtistDetailResponse detail(Integer userSeq, Integer artistSeq) {
        ArtistListRow artist = requireArtist(artistSeq);

        Subscription subscription = subscriptionRepository
                .findByIdUserSeqAndIdArtistSeq(userSeq, artistSeq)
                .orElse(null);
        Instant passExpiry = motifPassPriceRepository
                .findActivePassExpiry(userSeq, artistSeq)
                .orElse(null);

        return new ArtistDetailResponse(
                artist.artistSeq(),
                artist.name(),
                artist.groupName(),
                mediaUrlResolver.resolve(artist.profileImageKey()),
                artist.bio(),
                artist.subscriberCount(),
                challengeCount(artistSeq),
                subscription != null,
                subscribedDays(subscription),
                ArtistDetailResponse.MotifPass.of(passExpiry),
                // ponytail: §1.7 보호 등급 URL 을 만들 수단이 아직 없어 빈 배열로 둔다.
                // CloudFront signed 쿠키 발급(키페어·만료)이 붙으면 여기서
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
     * §5.3·§5.4 공통 404 — 미존재·삭제 아티스트는 {@code ARTIST_NOT_FOUND} 다.
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
