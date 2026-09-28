package com.ssafy.motif.coreapi.domain.artist.service;

import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.coreapi.domain.artist.ArtistErrorCode;
import com.ssafy.motif.coreapi.domain.artist.dto.SubscriptionResponse;
import com.ssafy.motif.domain.core.artist.Artist;
import com.ssafy.motif.domain.core.artist.ArtistRepository;
import com.ssafy.motif.domain.core.artist.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 구독 / 구독 취소 (API 명세 §5.6 · §5.7) — S15P21D104-59. 무료 팔로우이며
 * <b>패스 구매(§11.4)와 완전히 별개</b>다.
 *
 * <p><b>{@link ArtistQueryService} 와 나란한 별개 서비스다.</b> 그쪽은
 * {@code @Transactional(readOnly = true)} 로 선언돼 있어 쓰기를 한 줄이라도 섞으면 그
 * 표시가 거짓말이 된다({@code UserCommandService} 가 갈라진 것과 같은 근거).
 *
 * <p><b>멱등성이 애플리케이션 분기가 아니라 DB 제약에서 나온다.</b> "구독 중인가"를 묻고
 * 답에 따라 갈라지면 묻는 시점과 쓰는 시점이 벌어져, 동시에 들어온 두 요청이 카운터를 두 번
 * 움직인다. 대신 삽입·삭제가 <b>실제로 몇 행을 건드렸는지</b>를 보고 그때만 카운터를
 * 움직인다 — 복합 PK 가 판정자다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class SubscriptionCommandService {

    private final ArtistRepository artistRepository;
    private final SubscriptionRepository subscriptionRepository;

    /**
     * §5.6 구독.
     *
     * <p>삭제된 아티스트는 <b>구독할 수 없다</b>(§1.8 — 없는 것으로 답한다).
     */
    public SubscriptionResponse subscribe(Integer userSeq, Integer artistSeq) {
        Artist artist = artistRepository.findByArtistSeqAndDeletedFalse(artistSeq)
                .orElseThrow(() -> new BusinessException(
                        ArtistErrorCode.ARTIST_NOT_FOUND, "artistSeq: " + artistSeq));

        if (subscriptionRepository.insertIfAbsent(userSeq, artist.getArtistSeq()) == 1) {
            artistRepository.increaseSubscriberCount(List.of(artistSeq));
        }
        return new SubscriptionResponse(true, currentCount(artistSeq));
    }

    /**
     * §5.7 구독 취소.
     *
     * <p><b>삭제된 아티스트도 해제할 수 있다.</b> 명세가 404 를 "구독 신규 생성 시"로
     * 한정했고, 막아 두면 사용자가 그 구독을 <b>영원히 정리할 수 없다</b> — §4.6 이 삭제
     * 아티스트를 목록에서 숨기므로 화면에서 보이지도 않는 구독이 카운터에만 남는다.
     * 행 자체가 없을 때만 404 다(카운터를 읽을 대상이 없다).
     */
    public SubscriptionResponse unsubscribe(Integer userSeq, Integer artistSeq) {
        if (!artistRepository.existsById(artistSeq)) {
            throw new BusinessException(
                    ArtistErrorCode.ARTIST_NOT_FOUND, "artistSeq: " + artistSeq);
        }

        if (subscriptionRepository.deleteIfPresent(userSeq, artistSeq) == 1) {
            artistRepository.decreaseSubscriberCount(artistSeq);
        }
        return new SubscriptionResponse(false, currentCount(artistSeq));
    }

    /**
     * 증감이 반영된 카운터를 <b>다시 읽는다</b>.
     *
     * <p>앞의 벌크 UPDATE 가 영속성 컨텍스트를 우회하므로 미리 로드해 둔 {@link Artist} 는
     * 옛 값을 들고 있다. 두 카운터 메서드의 {@code clearAutomatically} 가 컨텍스트를 비워
     * 이 조회가 DB 를 읽게 한다 — 그 플래그가 빠지면 여기서 <b>증감 전 값</b>이 나간다.
     */
    private int currentCount(Integer artistSeq) {
        return artistRepository.findById(artistSeq)
                .map(Artist::getSubscriberCount)
                .orElseThrow(() -> new BusinessException(
                        ArtistErrorCode.ARTIST_NOT_FOUND, "artistSeq: " + artistSeq));
    }
}
