package com.ssafy.motif.coreapi.domain.video;

import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.coreapi.domain.video.dto.VideoItem;
import com.ssafy.motif.coreapi.domain.video.service.VideoFeedQueryService;
import com.ssafy.motif.coreapi.domain.video.service.VideoQueryService;
import com.ssafy.motif.domain.core.video.Visibility;
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
 * §8.2 참여 영상 단건을 실제 DB에 대고 검증한다.
 *
 * <p>여기서 고정하는 것은 <b>무엇이 404 인가</b>다. 명세 §8.2 는 성격이 다른 다섯 가지를
 * 한 코드로 묶었는데(미존재·삭제·삭제 사용자·타인 PRIVATE·삭제된 챌린지), 그중 넷은
 * JPQL 조인 조건이라 쿼리가 실제로 돌아야 드러난다.
 *
 * <p><b>§8.1 피드와 같은 항목을 내는지도 본다.</b> 명세가 두 응답을 같은 형태로 못박았고
 * 조립을 {@code VideoItemAssembler} 하나로 모은 근거가 그것이다 — 갈라지면 뷰어가 진입
 * 경로에 따라 다른 것을 재생한다.
 *
 * <p>실행 전 {@code backend/local-infra && docker compose up -d --wait}.
 */
@Tag("local-db")
@SpringBootTest
@Transactional
class VideoDetailLocalDbTest {

    @Autowired
    private VideoQueryService videoQueryService;
    @Autowired
    private VideoFeedQueryService videoFeedQueryService;
    @PersistenceContext
    private EntityManager entityManager;

    private VideoTestData data;
    private Integer me;
    private Integer other;
    private Integer artistSeq;
    private Integer challengeSeq;

    @BeforeEach
    void setUp() {
        data = new VideoTestData(entityManager);
        me = data.newUser();
        other = data.newUser();
        artistSeq = data.newArtist();
        challengeSeq = data.newChallenge(artistSeq, data.newSong(me), false);
    }

    // --- 성공 ---------------------------------------------------------------

    @Test
    @DisplayName("단건 JPQL 이 돈다 — 작성자·카운트·좋아요·본인 여부가 채워진다")
    void detailQueryRuns() {
        Long 남의것 = video().owner(other).views(300L).likes(12).comments(3).artistLike().insert();
        data.execute("insert into motif_core.video_likes (video_seq, user_seq) values (?1, ?2)",
                남의것, me);

        VideoItem item = videoQueryService.detail(me, 남의것);

        assertThat(item.videoSeq()).isEqualTo(남의것);
        assertThat(item.challengeSeq()).isEqualTo(challengeSeq);
        assertThat(item.author().userSeq()).isEqualTo(other);
        assertThat(item.author().nickname()).isNotBlank();
        assertThat(item.viewCount()).isEqualTo(300L);
        assertThat(item.likeCount()).isEqualTo(12);
        assertThat(item.commentCount()).isEqualTo(3);
        assertThat(item.hasArtistLike()).isTrue();
        assertThat(item.hasArtistComment()).isFalse();
        assertThat(item.isLiked()).isTrue();
        assertThat(item.isMine()).isFalse();
        assertThat(item.commentEnabled()).isTrue();
        assertThat(item.visibility()).isEqualTo(Visibility.PUBLIC);
        assertThat(item.durationMs()).isEqualTo(15000);
        assertThat(item.regDttm()).isNotNull();
    }

    @Test
    @DisplayName("좋아요를 누르지 않았으면 isLiked=false 다 — 1건 조회가 빈 결과를 삼키지 않는다")
    void notLikedIsFalse() {
        Long 영상 = video().owner(other).insert();

        assertThat(videoQueryService.detail(me, 영상).isLiked()).isFalse();
    }

    @Test
    @DisplayName("내 PRIVATE 은 200 이고 core-api 재생목록 주소를 받는다 (§1.7)")
    void myPrivateVideoIsVisible() {
        Long 내비공개 = video().owner(me).privateVisibility().insert();

        VideoItem item = videoQueryService.detail(me, 내비공개);

        assertThat(item.visibility()).isEqualTo(Visibility.PRIVATE);
        assertThat(item.isMine()).isTrue();
        assertThat(item.videoUrl())
                .startsWith("/api/v1/media/")
                .endsWith("/master.m3u8");
    }

    @Test
    @DisplayName("관심 없음 한 영상도 열린다 — hide 는 §8.1 피드에서 빼는 장치다")
    void hiddenVideoIsStillReadable() {
        Long 숨긴것 = video().owner(other).insert();
        data.execute("insert into motif_core.video_hides (user_seq, video_seq) values (?1, ?2)",
                me, 숨긴것);

        assertThat(videoQueryService.detail(me, 숨긴것).videoSeq()).isEqualTo(숨긴것);
    }

    @Test
    @DisplayName("§8.1 피드 항목과 §8.2 단건이 같은 항목이다 — 조립 지점이 하나다")
    void feedItemAndDetailAreIdentical() {
        Long 영상 = video().owner(other).views(42L).likes(7).comments(2).artistComment().insert();
        data.execute("insert into motif_core.video_likes (video_seq, user_seq) values (?1, ?2)",
                영상, me);

        VideoItem fromFeed = videoFeedQueryService.feed(me, challengeSeq, null, 10).list().stream()
                .filter(v -> v.videoSeq().equals(영상))
                .findFirst()
                .orElseThrow();

        assertThat(videoQueryService.detail(me, 영상)).isEqualTo(fromFeed);
    }

    // --- 404 ----------------------------------------------------------------

    @Test
    @DisplayName("없는 영상은 404 다")
    void unknownVideoIs404() {
        assertNotFound(9_999_999_999L);
    }

    @Test
    @DisplayName("삭제된 영상은 404 다")
    void deletedVideoIs404() {
        assertNotFound(video().owner(other).deleted().insert());
    }

    @Test
    @DisplayName("삭제된 사용자의 영상은 404 다 — §1.8 공통 비노출")
    void videoOfDeletedUserIs404() {
        Integer 탈퇴자 = data.newUser();
        Long 탈퇴자영상 = video().owner(탈퇴자).insert();
        data.execute("update motif_core.users set is_deleted = true where user_seq = ?1", 탈퇴자);

        assertNotFound(탈퇴자영상);
    }

    @Test
    @DisplayName("삭제된 챌린지에 속한 영상은 404 다 — 영상 자체는 살아 있어도 그렇다")
    void videoOfDeletedChallengeIs404() {
        Integer 삭제된챌린지 = data.newChallenge(artistSeq, data.newSong(me), true);
        Long 영상 = data.video(삭제된챌린지, artistSeq).owner(other).insert();

        assertNotFound(영상);
    }

    @Test
    @DisplayName("타인의 PRIVATE 은 403 이 아니라 404 다 — 존재 은닉")
    void othersPrivateVideoIs404() {
        assertNotFound(video().owner(other).privateVisibility().insert());
    }

    // --- 도우미 -------------------------------------------------------------

    private void assertNotFound(Long videoSeq) {
        assertThatThrownBy(() -> videoQueryService.detail(me, videoSeq))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(VideoErrorCode.VIDEO_NOT_FOUND);
    }

    private VideoTestData.VideoFixture video() {
        return data.video(challengeSeq, artistSeq);
    }
}
