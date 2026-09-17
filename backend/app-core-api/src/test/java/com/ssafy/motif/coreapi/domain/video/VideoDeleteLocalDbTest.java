package com.ssafy.motif.coreapi.domain.video;

import com.ssafy.motif.common.error.CommonErrorCode;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * §8.10 게시 영상 삭제를 실제 DB에 대고 검증한다 (S15P21D104-209).
 *
 * <p>고정하는 것은 <b>카운터가 정확히 한 번만 내려가는지</b>와 <b>삭제가 목록 전체에
 * 반영되는지</b>다. 소프트 삭제라 "지워졌다" 를 응답으로 확인할 수 없어, §8.1 피드와
 * §8.2 단건으로 확인한다.
 *
 * <p>실행 전 {@code backend/local-infra && docker compose up -d --wait}.
 */
@Tag("local-db")
@SpringBootTest
@Transactional
class VideoDeleteLocalDbTest {

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

    // --- 삭제 ---------------------------------------------------------------

    @Test
    @DisplayName("삭제하면 §8.1 피드와 §8.2 단건에서 사라진다")
    void deletedVideoDisappears() {
        Long 영상 = video().owner(me).insert();

        videoCommandService.delete(me, 영상);

        assertThat(feedSeqs()).doesNotContain(영상);
        assertThatThrownBy(() -> videoQueryService.detail(me, 영상))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(VideoErrorCode.VIDEO_NOT_FOUND);
        assertThat(isDeleted(영상)).isTrue();
    }

    @Test
    @DisplayName("참여 수가 정확히 1 내려간다")
    void participantCountDropsByOne() {
        Long 영상 = video().owner(me).insert();
        setParticipantCount(3);

        videoCommandService.delete(me, 영상);

        assertThat(participantCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("두 번째 삭제는 404 이고 참여 수는 그대로다")
    void secondDeleteIs404AndDoesNotDecrementAgain() {
        Long 영상 = video().owner(me).insert();
        setParticipantCount(3);
        videoCommandService.delete(me, 영상);

        assertNotFound(영상);

        assertThat(participantCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("참여 수가 0이면 더 내려가지 않는다 — CHECK 제약이 500 을 내지 않게")
    void participantCountNeverGoesNegative() {
        Long 영상 = video().owner(me).insert();
        setParticipantCount(0);

        videoCommandService.delete(me, 영상);

        assertThat(participantCount()).isZero();
    }

    @Test
    @DisplayName("내 영상 여러 개를 지우면 지운 만큼만 내려간다")
    void deletingTwoVideosDropsCountTwice() {
        Long 첫번째 = video().owner(me).insert();
        Long 두번째 = video().owner(me).insert();
        setParticipantCount(5);

        videoCommandService.delete(me, 첫번째);
        videoCommandService.delete(me, 두번째);

        assertThat(participantCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("PRIVATE 영상도 본인이면 지울 수 있다")
    void canDeleteMyPrivateVideo() {
        Long 내비공개 = video().owner(me).privateVisibility().insert();

        videoCommandService.delete(me, 내비공개);

        assertThat(isDeleted(내비공개)).isTrue();
    }

    @Test
    @DisplayName("신고된 영상도 지울 수 있다 — 신고 이력은 남는다")
    void deletingKeepsReportRow() {
        Long 영상 = video().owner(me).insert();
        data.execute("""
                insert into motif_core.reports (reporter_seq, video_seq, reason_code)
                values (?1, ?2, 'SPAM')
                """, other, 영상);

        videoCommandService.delete(me, 영상);

        assertThat(count("select count(*) from motif_core.reports where video_seq = ?1", 영상))
                .isEqualTo(1);
    }

    // --- 권한 ---------------------------------------------------------------

    @Test
    @DisplayName("남의 PUBLIC 영상은 403 이고 지워지지 않는다")
    void othersPublicVideoIs403() {
        Long 남의것 = video().owner(other).insert();

        assertThatThrownBy(() -> videoCommandService.delete(me, 남의것))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.FORBIDDEN);

        assertThat(isDeleted(남의것)).isFalse();
    }

    @Test
    @DisplayName("남의 PRIVATE 영상은 403 이 아니라 404 다 — 존재 은닉이 앞선다")
    void othersPrivateVideoIs404() {
        assertNotFound(video().owner(other).privateVisibility().insert());
    }

    @Test
    @DisplayName("없는 영상은 404 다")
    void unknownVideoIs404() {
        assertNotFound(9_999_999_999L);
    }

    @Test
    @DisplayName("삭제된 챌린지에 속한 영상은 404 다")
    void videoOfDeletedChallengeIs404() {
        Integer 삭제된챌린지 = data.newChallenge(artistSeq, data.newSong(me), true);

        assertNotFound(data.video(삭제된챌린지, artistSeq).owner(me).insert());
    }

    // --- 도우미 -------------------------------------------------------------

    private void assertNotFound(Long videoSeq) {
        assertThatThrownBy(() -> videoCommandService.delete(me, videoSeq))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(VideoErrorCode.VIDEO_NOT_FOUND);
    }

    private List<Long> feedSeqs() {
        return videoFeedQueryService.feed(me, challengeSeq, null, 100).list().stream()
                .map(VideoItem::videoSeq)
                .toList();
    }

    private void setParticipantCount(int value) {
        data.execute("update motif_core.challenges set participant_count = ?1 where challenge_seq = ?2",
                value, challengeSeq);
    }

    private int participantCount() {
        return count("select participant_count from motif_core.challenges where challenge_seq = ?1",
                challengeSeq);
    }

    private boolean isDeleted(Long videoSeq) {
        return count("""
                select case when is_deleted then 1 else 0 end
                  from motif_core.participation_videos where video_seq = ?1
                """, videoSeq) == 1;
    }

    /** 벌크 UPDATE 가 영속성 컨텍스트를 우회하므로 DB 를 직접 본다. */
    private int count(String sql, Object param) {
        entityManager.flush();
        return ((Number) entityManager.createNativeQuery(sql)
                .setParameter(1, param)
                .getSingleResult()).intValue();
    }

    private VideoTestData.VideoFixture video() {
        return data.video(challengeSeq, artistSeq);
    }
}
