package com.ssafy.motif.coreapi.domain.challenge;

import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.coreapi.domain.challenge.service.ChallengeLikeService;
import com.ssafy.motif.coreapi.domain.video.VideoTestData;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 챌린지 좋아요 (API 명세 §7.3) — S15P21D104-617.
 *
 * <p>§8.3 영상 좋아요와 같은 것을 본다 — 카운터가 <b>상태가 실제로 바뀐 만큼만</b>
 * 움직이는지(멱등), 해제가 하드 삭제인지, 삭제된 챌린지가 404 인지.
 *
 * <p>멱등이 가장 깨지기 쉽다. "이미 눌렀나" 를 묻고 갈라지는 구현으로 바꿔도 단일 호출
 * 테스트는 전부 통과하는데, 같은 요청을 두 번 보내면 {@code like_count} 가 2가 된다.
 *
 * <p>실행 전 {@code backend/local-infra && docker compose up -d --wait}.
 */
@Tag("local-db")
@SpringBootTest
@Transactional
class ChallengeLikeLocalDbTest {

    @Autowired
    private ChallengeLikeService challengeLikeService;
    @PersistenceContext
    private EntityManager entityManager;

    private VideoTestData data;
    private Integer challengeSeq;

    @BeforeEach
    void setUp() {
        data = new VideoTestData(entityManager);
        challengeSeq = data.newChallenge(data.newArtist(), data.newSong(data.newUser()), false);
    }

    @Test
    @DisplayName("좋아요는 멱등이다 — 두 번 눌러도 like_count 는 1이다")
    void likeIsIdempotent() {
        Integer fan = data.newUser();

        challengeLikeService.like(fan, challengeSeq);
        challengeLikeService.like(fan, challengeSeq);

        assertThat(likeCount()).isEqualTo(1);
        assertThat(likeRows()).isEqualTo(1);
    }

    @Test
    @DisplayName("해제도 멱등이고 하드 삭제다 — 0 아래로 내려가지 않는다")
    void unlikeIsIdempotentAndHard() {
        Integer fan = data.newUser();
        challengeLikeService.like(fan, challengeSeq);

        challengeLikeService.unlike(fan, challengeSeq);
        challengeLikeService.unlike(fan, challengeSeq);

        assertThat(likeCount()).isZero();
        assertThat(likeRows()).isZero();
    }

    @Test
    @DisplayName("여러 사람이 누르면 각자 한 행씩 쌓인다")
    void likesAccumulatePerUser() {
        challengeLikeService.like(data.newUser(), challengeSeq);
        challengeLikeService.like(data.newUser(), challengeSeq);

        assertThat(likeCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("삭제된 챌린지는 404 — 생성과 해제가 같은 판정을 거친다")
    void deletedChallengeIsNotFound() {
        Integer deleted = data.newChallenge(data.newArtist(), data.newSong(data.newUser()), true);
        Integer fan = data.newUser();

        for (Runnable call : new Runnable[]{
                () -> challengeLikeService.like(fan, deleted),
                () -> challengeLikeService.unlike(fan, deleted)}) {
            assertThatThrownBy(call::run)
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode().getCode())
                    .isEqualTo("CHALLENGE_NOT_FOUND");
        }
    }

    private int likeCount() {
        entityManager.clear();
        return ((Number) entityManager.createNativeQuery(
                        "select like_count from motif_core.challenges where challenge_seq = ?1")
                .setParameter(1, challengeSeq)
                .getSingleResult()).intValue();
    }

    private int likeRows() {
        return ((Number) entityManager.createNativeQuery(
                        "select count(*) from motif_core.challenge_likes where challenge_seq = ?1")
                .setParameter(1, challengeSeq)
                .getSingleResult()).intValue();
    }
}
