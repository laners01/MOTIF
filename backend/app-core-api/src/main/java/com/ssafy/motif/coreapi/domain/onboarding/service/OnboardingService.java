package com.ssafy.motif.coreapi.domain.onboarding.service;

import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.coreapi.domain.artist.ArtistErrorCode;
import com.ssafy.motif.coreapi.domain.onboarding.OnboardingErrorCode;
import com.ssafy.motif.coreapi.domain.onboarding.dto.OnboardingRequest;
import com.ssafy.motif.coreapi.domain.onboarding.dto.OnboardingResponse;
import com.ssafy.motif.domain.core.artist.ArtistRepository;
import com.ssafy.motif.domain.core.artist.Subscription;
import com.ssafy.motif.domain.core.artist.SubscriptionRepository;
import com.ssafy.motif.domain.core.user.User;
import com.ssafy.motif.domain.core.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * 온보딩 완료 (명세 §3.3) — 구독 일괄 생성과 닉네임 확정을 <b>한 트랜잭션</b>으로 묶는다.
 *
 * <p>화면은 2단계지만 제출은 1회다. 구독만 되고 닉네임이 안 바뀌는 중간 상태가 남으면
 * "온보딩 완료 = 구독 1건 이상" 판정이 사실과 어긋나므로, 어느 단계에서 실패하든
 * 전부 롤백한다.
 */
@Service
@RequiredArgsConstructor
public class OnboardingService {

    private final UserRepository userRepository;
    private final ArtistRepository artistRepository;
    private final SubscriptionRepository subscriptionRepository;

    @Transactional
    public OnboardingResponse complete(Integer userSeq, OnboardingRequest request) {
        if (subscriptionRepository.existsByIdUserSeq(userSeq)) {
            throw new BusinessException(OnboardingErrorCode.ALREADY_ONBOARDED,
                    "이미 구독이 있는 사용자: userSeq=" + userSeq);
        }

        Set<Integer> artistSeqs = request.distinctArtistSeqs();
        verifyAllArtistsExist(artistSeqs);

        subscriptionRepository.saveAll(
                artistSeqs.stream().map(artistSeq -> Subscription.of(userSeq, artistSeq)).toList());
        artistRepository.increaseSubscriberCount(artistSeqs);

        User user = userRepository.findById(userSeq)
                .orElseThrow(() -> new IllegalStateException(
                        "인증된 토큰의 사용자가 users 에 없다 — userSeq=" + userSeq));
        user.changeNickname(request.nickname());

        return new OnboardingResponse(artistSeqs.size(), user.getNickname());
    }

    /**
     * 요청한 아티스트가 전부 존재하고 비삭제인지 확인한다. 하나라도 어긋나면 404 다 —
     * 어느 것이 문제인지는 응답에 담지 않으므로(명세 §3.3) 개수 비교로 충분하다.
     */
    private void verifyAllArtistsExist(Set<Integer> artistSeqs) {
        long found = artistRepository.countByArtistSeqInAndDeletedFalse(artistSeqs);
        if (found != artistSeqs.size()) {
            throw new BusinessException(ArtistErrorCode.ARTIST_NOT_FOUND,
                    "요청 %d명 중 %d명만 유효: %s".formatted(artistSeqs.size(), found, artistSeqs));
        }
    }
}
