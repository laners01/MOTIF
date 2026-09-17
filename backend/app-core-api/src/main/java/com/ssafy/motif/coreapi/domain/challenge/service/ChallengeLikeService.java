package com.ssafy.motif.coreapi.domain.challenge.service;

import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.coreapi.domain.challenge.ChallengeErrorCode;
import com.ssafy.motif.domain.core.challenge.ChallengeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 챌린지 좋아요 토글 (API 명세 §7.3).
 *
 * <p><b>멱등이 애플리케이션 분기가 아니라 DB 제약에서 나온다.</b> "이미 눌렀나" 를 묻고
 * 답에 따라 갈라지면 묻는 시점과 쓰는 시점이 벌어져, 동시에 들어온 두 요청이
 * {@code challenges.like_count} 를 두 번 움직인다. 대신 삽입·삭제가 <b>실제로 몇 행을
 * 건드렸는지</b>를 보고 그때만 카운터를 움직인다 — {@code challenge_likes} 의 복합 PK 가
 * 판정자다(§8.3 · §9.5 · §10.2 와 같은 방식).
 *
 * <p><b>응답이 없다(204).</b> {@code isLiked}·{@code likeCount} 를 돌려주지 않으므로
 * 클라이언트가 낙관적으로 반영하고 실패 시 롤백한다(§1.2 봉투 예외). 정확한 값이
 * 필요하면 §7.1·§7.2 를 다시 받는다.
 *
 * <p><b>영상 좋아요(§8.3)보다 판정이 단순하다.</b> 챌린지에는 {@code PRIVATE} 이 없어
 * 존재 은닉을 따질 것이 없고({@code VideoAccessGuard} 에 해당하는 장치가 필요 없다),
 * {@code has_artist_like} 같은 파급 플래그도 없다 — 삭제 여부 하나만 본다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ChallengeLikeService {

    private final ChallengeRepository challengeRepository;

    /** §7.3 좋아요 — 이미 누른 상태면 아무것도 바뀌지 않는다(그래도 204). */
    public void like(Integer userSeq, Integer challengeSeq) {
        toggle(userSeq, challengeSeq, true);
    }

    /** §7.3 해제 — 누르지 않은 상태면 아무것도 바뀌지 않는다(그래도 204). */
    public void unlike(Integer userSeq, Integer challengeSeq) {
        toggle(userSeq, challengeSeq, false);
    }

    /**
     * 생성과 해제가 <b>완전히 같은 판정</b>을 거친다 — 한쪽만 느슨하면 삭제된 챌린지에서
     * 한 방향으로만 카운터가 움직여 되돌릴 수 없는 상태가 남는다(§10.2 와 같은 근거).
     */
    private void toggle(Integer userSeq, Integer challengeSeq, boolean on) {
        if (!challengeRepository.existsByChallengeSeqAndDeletedFalse(challengeSeq)) {
            throw new BusinessException(ChallengeErrorCode.CHALLENGE_NOT_FOUND,
                    "challengeSeq: " + challengeSeq);
        }

        int changed = on
                ? challengeRepository.insertLikeIfAbsent(challengeSeq, userSeq)
                : challengeRepository.deleteLikeIfPresent(challengeSeq, userSeq);
        if (changed == 1) {
            challengeRepository.addLikeCount(challengeSeq, on ? 1 : -1);
        }
    }
}
