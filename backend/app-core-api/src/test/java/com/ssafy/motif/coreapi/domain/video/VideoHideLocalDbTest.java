package com.ssafy.motif.coreapi.domain.video;

import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.coreapi.domain.video.dto.VideoItem;
import com.ssafy.motif.coreapi.domain.video.service.VideoCommandService;
import com.ssafy.motif.coreapi.domain.video.service.VideoFeedQueryService;
import com.ssafy.motif.coreapi.domain.video.service.VideoQueryService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * §8.6 관심 없음을 실제 DB에 대고 검증한다 (S15P21D104-164).
 *
 * <p><b>이 API 의 결과는 다른 API 에서 드러난다.</b> 응답이 {@code data: null} 뿐이라
 * 숨김이 실제로 걸렸는지는 §8.1 피드에서 빠지는지로 확인해야 하고, 접근 차단이 아니라는
 * 것도 §8.2 단건이 열리는지로 확인해야 한다 — 그래서 세 서비스를 함께 부른다.
 *
 * <p>멱등도 DB 제약에서 나오므로({@code pk_video_hides} + {@code on conflict do nothing})
 * 실제 INSERT 가 두 번 돌아야 확인된다.
 *
 * <p>실행 전 {@code backend/local-infra && docker compose up -d --wait}.
 */
@Tag("local-db")
@SpringBootTest
@Transactional
class VideoHideLocalDbTest {

    @Autowired
    private VideoCommandService videoCommandService;
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

    // --- 숨김 ---------------------------------------------------------------

    @Test
    @DisplayName("숨기면 내 §8.1 피드에서 빠진다 — 남의 피드에는 그대로 있다")
    void hiddenVideoLeavesMyFeedOnly() {
        Long 영상 = video().owner(other).insert();

        videoCommandService.hide(me, 영상);

        assertThat(feedSeqs(me)).doesNotContain(영상);
        assertThat(feedSeqs(other)).contains(영상);
    }

    @Test
    @DisplayName("숨겨도 §8.2 단건은 열린다 — 접근 차단이 아니다")
    void hiddenVideoIsStillReadable() {
        Long 영상 = video().owner(other).insert();

        videoCommandService.hide(me, 영상);

        assertThat(videoQueryService.detail(me, 영상).videoSeq()).isEqualTo(영상);
    }

    @Test
    @DisplayName("두 번 숨겨도 200 이고 행은 하나다 — 멱등")
    void hidingTwiceIsIdempotent() {
        Long 영상 = video().owner(other).insert();

        videoCommandService.hide(me, 영상);
        assertThatCode(() -> videoCommandService.hide(me, 영상)).doesNotThrowAnyException();

        assertThat(hideCount(me, 영상)).isEqualTo(1);
        assertThat(feedSeqs(me)).doesNotContain(영상);
    }

    @Test
    @DisplayName("본인 영상도 숨길 수 있다 — 막을 근거가 명세에 없다")
    void canHideOwnVideo() {
        Long 내영상 = video().owner(me).insert();

        videoCommandService.hide(me, 내영상);

        assertThat(feedSeqs(me)).doesNotContain(내영상);
    }

    @Test
    @DisplayName("숨김은 사용자별이다 — 다른 사람이 같은 영상을 따로 숨긴다")
    void hidesArePerUser() {
        Long 영상 = video().owner(other).insert();
        Integer 제삼자 = data.newUser();

        videoCommandService.hide(me, 영상);
        videoCommandService.hide(제삼자, 영상);

        assertThat(hideCount(me, 영상)).isEqualTo(1);
        assertThat(hideCount(제삼자, 영상)).isEqualTo(1);
        assertThat(feedSeqs(other)).contains(영상);
    }

    // --- 404 (§8.2 와 같은 규칙) ----------------------------------------------

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
    @DisplayName("삭제된 사용자의 영상은 404 다")
    void videoOfDeletedUserIs404() {
        Integer 탈퇴자 = data.newUser();
        Long 탈퇴자영상 = video().owner(탈퇴자).insert();
        data.execute("update motif_core.users set is_deleted = true where user_seq = ?1", 탈퇴자);

        assertNotFound(탈퇴자영상);
    }

    @Test
    @DisplayName("삭제된 챌린지에 속한 영상은 404 다")
    void videoOfDeletedChallengeIs404() {
        Integer 삭제된챌린지 = data.newChallenge(artistSeq, data.newSong(me), true);

        assertNotFound(data.video(삭제된챌린지, artistSeq).owner(other).insert());
    }

    @Test
    @DisplayName("타인의 PRIVATE 은 404 이고 행도 남지 않는다 — 존재 은닉")
    void othersPrivateVideoIs404() {
        Long 남의비공개 = video().owner(other).privateVisibility().insert();

        assertNotFound(남의비공개);
        assertThat(hideCount(me, 남의비공개)).isZero();
    }

    // --- 도우미 -------------------------------------------------------------

    private void assertNotFound(Long videoSeq) {
        assertThatThrownBy(() -> videoCommandService.hide(me, videoSeq))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(VideoErrorCode.VIDEO_NOT_FOUND);
    }

    private List<Long> feedSeqs(Integer userSeq) {
        return videoFeedQueryService.feed(userSeq, challengeSeq, null, 100).list().stream()
                .map(VideoItem::videoSeq)
                .toList();
    }

    private int hideCount(Integer userSeq, Long videoSeq) {
        return ((Number) entityManager.createNativeQuery("""
                        select count(*) from motif_core.video_hides
                         where user_seq = ?1 and video_seq = ?2
                        """)
                .setParameter(1, userSeq)
                .setParameter(2, videoSeq)
                .getSingleResult()).intValue();
    }

    private VideoTestData.VideoFixture video() {
        return data.video(challengeSeq, artistSeq);
    }
}
