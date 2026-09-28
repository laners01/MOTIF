package com.ssafy.motif.coreapi.domain.video;

import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.coreapi.domain.video.service.VideoLikeService;
import com.ssafy.motif.domain.core.video.ParticipationVideoRepository;
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
 * 참여 영상 좋아요 (API 명세 §8.3) — S15P21D104-145.
 *
 * <p>보는 것 넷이다 — ① 카운터가 <b>상태가 실제로 바뀐 만큼만</b> 움직이는가(멱등)
 * ② 해제가 하드 삭제인가 ③ 아티스트가 누르면 {@code has_artist_like} 가 따라 움직이는가
 * ④ 안 보이는 영상은 404 인가.
 *
 * <p>①이 이 기능에서 가장 깨지기 쉽다. "이미 눌렀나" 를 묻고 갈라지는 구현으로 바꿔도
 * 단일 호출 테스트는 전부 통과하는데, 같은 요청을 두 번 보내면 {@code like_count} 가
 * 2가 된다 — 되돌릴 방법이 배치밖에 없는 종류의 어긋남이다.
 *
 * <p>실행 전 {@code backend/local-infra && docker compose up -d --wait}.
 */
@Tag("local-db")
@SpringBootTest
@Transactional
class VideoLikeLocalDbTest {

    @Autowired
    private VideoLikeService videoLikeService;
    @Autowired
    private ParticipationVideoRepository participationVideoRepository;
    @PersistenceContext
    private EntityManager entityManager;

    private VideoTestData data;
    private Integer artistSeq;
    private Integer owner;
    private Long videoSeq;

    @BeforeEach
    void setUp() {
        data = new VideoTestData(entityManager);
        artistSeq = data.newArtist();
        Integer challengeSeq = data.newChallenge(artistSeq, data.newSong(data.newUser()), false);
        owner = data.newUser();
        videoSeq = data.video(challengeSeq, artistSeq).owner(owner).insert();
    }

    @Test
    @DisplayName("좋아요는 멱등이다 — 두 번 눌러도 like_count 는 1이다")
    void likeIsIdempotent() {
        Integer fan = data.newUser();

        videoLikeService.like(fan, videoSeq);
        videoLikeService.like(fan, videoSeq);

        assertThat(likeCount()).isEqualTo(1);
        assertThat(likeRows()).isEqualTo(1);
    }

    @Test
    @DisplayName("해제도 멱등이고 하드 삭제다 — 누르지 않은 상태에서 불러도 0 아래로 안 내려간다")
    void unlikeIsIdempotentAndHard() {
        Integer fan = data.newUser();
        videoLikeService.like(fan, videoSeq);

        videoLikeService.unlike(fan, videoSeq);
        videoLikeService.unlike(fan, videoSeq);

        assertThat(likeCount()).isZero();
        assertThat(likeRows()).isZero();          // 소프트 삭제가 아니다
    }

    @Test
    @DisplayName("아티스트가 누르면 has_artist_like 가 서고 해제하면 내려간다")
    void artistLikeTogglesFlag() {
        Integer artistUser = artistUserSeq();

        videoLikeService.like(artistUser, videoSeq);
        assertThat(hasArtistLike()).isTrue();

        videoLikeService.unlike(artistUser, videoSeq);
        assertThat(hasArtistLike()).isFalse();
    }

    @Test
    @DisplayName("팬이 눌러도 has_artist_like 는 서지 않는다")
    void fanLikeDoesNotSetFlag() {
        videoLikeService.like(data.newUser(), videoSeq);

        assertThat(likeCount()).isEqualTo(1);
        assertThat(hasArtistLike()).isFalse();
    }

    @Test
    @DisplayName("타인의 PRIVATE 영상은 404 — 좋아요가 존재를 알려 주는 탐지 수단이 되면 안 된다")
    void othersPrivateIsNotFound() {
        Integer challengeSeq = data.newChallenge(artistSeq, data.newSong(data.newUser()), false);
        Long privateSeq = data.video(challengeSeq, artistSeq)
                .owner(data.newUser()).privateVisibility().insert();
        Integer stranger = data.newUser();

        assertThatThrownBy(() -> videoLikeService.like(stranger, privateSeq))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode().getCode())
                .isEqualTo("VIDEO_NOT_FOUND");
    }

    private int likeCount() {
        entityManager.clear();
        return ((Number) entityManager.createNativeQuery(
                        "select like_count from motif_core.participation_videos where video_seq = ?1")
                .setParameter(1, videoSeq)
                .getSingleResult()).intValue();
    }

    private int likeRows() {
        return ((Number) entityManager.createNativeQuery(
                        "select count(*) from motif_core.video_likes where video_seq = ?1")
                .setParameter(1, videoSeq)
                .getSingleResult()).intValue();
    }

    private boolean hasArtistLike() {
        entityManager.clear();
        return (Boolean) entityManager.createNativeQuery(
                        "select has_artist_like from motif_core.participation_videos where video_seq = ?1")
                .setParameter(1, videoSeq)
                .getSingleResult();
    }

    private Integer artistUserSeq() {
        return participationVideoRepository.findArtistUserSeq(videoSeq).orElseThrow();
    }
}
