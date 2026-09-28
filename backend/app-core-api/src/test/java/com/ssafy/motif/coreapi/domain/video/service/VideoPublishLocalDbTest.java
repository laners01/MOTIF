package com.ssafy.motif.coreapi.domain.video.service;

import com.ssafy.motif.common.error.CommonErrorCode;
import com.ssafy.motif.common.error.ErrorCode;
import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.coreapi.domain.composite.CompositeErrorCode;
import com.ssafy.motif.coreapi.domain.video.VideoErrorCode;
import com.ssafy.motif.coreapi.domain.video.dto.VideoItem;
import com.ssafy.motif.coreapi.domain.video.dto.VideoPublishRequest;
import com.ssafy.motif.coreapi.global.media.MediaSigningTestSupport;
import com.ssafy.motif.coreapi.global.security.AuthenticatedUser;
import com.ssafy.motif.domain.core.artist.Artist;
import com.ssafy.motif.domain.core.artist.ArtistRepository;
import com.ssafy.motif.domain.core.challenge.Challenge;
import com.ssafy.motif.domain.core.challenge.ChallengeParticipant;
import com.ssafy.motif.domain.core.challenge.ChallengeParticipantRepository;
import com.ssafy.motif.domain.core.challenge.ChallengeRepository;
import com.ssafy.motif.domain.core.job.CompositeJob;
import com.ssafy.motif.domain.core.job.CompositeJobRepository;
import com.ssafy.motif.domain.core.job.JobType;
import com.ssafy.motif.domain.core.song.Song;
import com.ssafy.motif.domain.core.song.SongRepository;
import com.ssafy.motif.domain.core.user.User;
import com.ssafy.motif.domain.core.user.UserRepository;
import com.ssafy.motif.domain.core.user.UserRole;
import com.ssafy.motif.domain.core.video.ParticipationVideo;
import com.ssafy.motif.domain.core.video.ParticipationVideoRepository;
import com.ssafy.motif.domain.core.video.Visibility;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * §8.8 게시를 실제 DB 에 대고 검증한다 (S15P21D104-203).
 *
 * <p><b>키를 옮기지 않는다는 것이 이 테스트의 핵심이다.</b> 게시된 영상의
 * {@code video_key} 는 잡의 {@code result_video_key} 와 <b>글자 그대로 같아야</b> 하고,
 * {@code PUBLIC} 이어도 {@code private/} 프리픽스 그대로다(ADR-0013). 공개 사본 트리를
 * 되살리면 여기서 깨진다.
 *
 * <p>중복 게시 판정이 {@code uq_participation_videos_job} 이라 DB 없이는 확인할 수 없다.
 *
 * <p>실행 전 {@code backend/local-infra && docker compose up -d --wait}.
 */
@Tag("local-db")
@SpringBootTest
@Transactional
class VideoPublishLocalDbTest {

    /**
     * 보호 등급 URL 을 실제로 서명해 본다 — 키가 없으면 §8.2 조회가 500 이다(ADR-0013).
     * 키 생성은 {@link MediaSigningTestSupport} 가 이미 갖고 있다.
     */
    @DynamicPropertySource
    static void signing(DynamicPropertyRegistry registry) {
        MediaSigningTestSupport.register(registry);
    }

    @Autowired
    private VideoCommandService videoCommandService;
    @Autowired
    private VideoQueryService videoQueryService;
    @Autowired
    private CompositeJobRepository compositeJobRepository;
    @Autowired
    private ParticipationVideoRepository participationVideoRepository;
    @Autowired
    private ChallengeRepository challengeRepository;
    @Autowired
    private ChallengeParticipantRepository participantRepository;
    @Autowired
    private ArtistRepository artistRepository;
    @Autowired
    private SongRepository songRepository;
    @Autowired
    private UserRepository userRepository;
    @PersistenceContext
    private EntityManager entityManager;

    private Integer adminUserSeq;
    private Integer fanSeq;
    private Integer artistSeq;
    private Integer challengeSeq;

    @BeforeEach
    void setUp() {
        adminUserSeq = newUser().getUserSeq();
        fanSeq = newUser().getUserSeq();
        artistSeq = newArtist();
        challengeSeq = newChallenge();
    }

    // --- 성공 --------------------------------------------------------------------

    @Test
    @DisplayName("게시하면 잡 결과 키를 그대로 가리키는 행이 생기고 참여 수가 1 오른다")
    void publishCreatesRowPointingAtTheJobResult() {
        CompositeJob job = doneJob();
        int before = challengeRepository.findById(challengeSeq).orElseThrow().getParticipantCount();

        Long videoSeq = publish(job, Visibility.PUBLIC).videoSeq();

        ParticipationVideo video = participationVideoRepository.findById(videoSeq).orElseThrow();
        // 키를 만들지도 옮기지도 않는다 — 잡 결과와 글자 그대로 같다
        assertThat(video.getVideoKey()).isEqualTo(job.getResultVideoKey());
        assertThat(video.getThumbnailKey()).isEqualTo(job.getResultThumbnailKey());
        assertThat(video.getChallengeSeq()).isEqualTo(challengeSeq);
        assertThat(video.getArtistSeq()).isEqualTo(artistSeq);   // 잡이 아니라 챌린지에서
        assertThat(video.getUserSeq()).isEqualTo(fanSeq);
        assertThat(video.getJobSeq()).isEqualTo(job.getJobSeq());
        assertThat(video.getDurationMs()).isEqualTo(15_000);     // challenges.duration_ms
        assertThat(video.getVisibility()).isEqualTo(Visibility.PUBLIC);

        entityManager.flush();
        entityManager.clear();
        assertThat(challengeRepository.findById(challengeSeq).orElseThrow().getParticipantCount())
                .isEqualTo(before + 1);
    }

    @Test
    @DisplayName("PUBLIC 이어도 키는 private/ 그대로다 — 공개 사본 트리는 없다(ADR-0013)")
    void publicPublishDoesNotMoveTheKey() {
        CompositeJob job = doneJob();

        Long videoSeq = publish(job, Visibility.PUBLIC).videoSeq();

        assertThat(participationVideoRepository.findById(videoSeq).orElseThrow().getVideoKey())
                .startsWith("private/challenge/")
                .doesNotContain("public/");
        // 잡의 정본도 그대로다 — 게시가 result_video_key 를 바꾸지 않는다
        assertThat(reload(job).getResultVideoKey()).isEqualTo(job.getResultVideoKey());
    }

    @Test
    @DisplayName("게시 직후 §8.2 단건에 보인다")
    void publishedVideoIsVisibleInDetail() {
        CompositeJob job = doneJob();

        Long videoSeq = publish(job, Visibility.PUBLIC).videoSeq();

        VideoItem item = videoQueryService.detail(fanSeq, videoSeq);
        assertThat(item.videoSeq()).isEqualTo(videoSeq);
        assertThat(item.challengeSeq()).isEqualTo(challengeSeq);
        assertThat(item.description()).isEqualTo("재밌었다!");
        assertThat(item.visibility()).isEqualTo(Visibility.PUBLIC);
    }

    @Test
    @DisplayName("PRIVATE 은 본인만 본다 — 남에게는 404 다")
    void privateVideoIsHiddenFromOthers() {
        CompositeJob job = doneJob();
        Long videoSeq = publish(job, Visibility.PRIVATE).videoSeq();
        Integer stranger = newUser().getUserSeq();

        assertThat(videoQueryService.detail(fanSeq, videoSeq).videoSeq()).isEqualTo(videoSeq);
        assertThatThrownBy(() -> videoQueryService.detail(stranger, videoSeq))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(VideoErrorCode.VIDEO_NOT_FOUND);
    }

    // --- 실패 --------------------------------------------------------------------

    @Test
    @DisplayName("ARTIST 계정은 403 — 잡을 보기도 전에 막힌다")
    void artistCannotPublish() {
        CompositeJob job = doneJob();

        assertThatThrownBy(() -> videoCommandService.publish(
                new AuthenticatedUser(fanSeq, UserRole.ARTIST), request(job, Visibility.PUBLIC)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("두 번 게시하면 409 — uq_participation_videos_job 이 판정한다")
    void secondPublishIsConflict() {
        CompositeJob job = doneJob();
        publish(job, Visibility.PUBLIC);

        assertFails(job, VideoErrorCode.ALREADY_PUBLISHED);
    }

    @Test
    @DisplayName("DONE 이 아니면 409")
    void notDoneJobIsConflict() {
        CompositeJob job = compositeJobRepository.saveAndFlush(CompositeJob.create(
                UUID.randomUUID(), JobType.CHALLENGE, fanSeq, challengeSeq, null, newSlot(),
                "raw/" + UUID.randomUUID() + ".mp4"));

        assertFails(job, VideoErrorCode.JOB_NOT_DONE);
    }

    @Test
    @DisplayName("게시 시점에 챌린지가 삭제됐으면 409")
    void deletedChallengeIsConflict() {
        CompositeJob job = doneJob();
        challengeRepository.findById(challengeSeq).orElseThrow().softDelete();
        entityManager.flush();

        assertFails(job, VideoErrorCode.CHALLENGE_DELETED);
    }

    @Test
    @DisplayName("타인 잡·MOTIF 잡·없는 잡은 전부 404 — 존재를 숨긴다")
    void foreignOrMotifJobIsNotFound() {
        CompositeJob mine = doneJob();
        Integer stranger = newUser().getUserSeq();

        assertThatThrownBy(() -> videoCommandService.publish(
                new AuthenticatedUser(stranger, UserRole.USER), request(mine, Visibility.PUBLIC)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CompositeErrorCode.JOB_NOT_FOUND);

        assertThatThrownBy(() -> videoCommandService.publish(
                new AuthenticatedUser(fanSeq, UserRole.USER),
                new VideoPublishRequest(UUID.randomUUID(), "x", Visibility.PUBLIC, true)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CompositeErrorCode.JOB_NOT_FOUND);
    }

    // --- 픽스처 -------------------------------------------------------------------------

    private com.ssafy.motif.coreapi.domain.video.dto.VideoPublishResponse publish(
            CompositeJob job, Visibility visibility) {
        return videoCommandService.publish(
                new AuthenticatedUser(fanSeq, UserRole.USER), request(job, visibility));
    }

    private static VideoPublishRequest request(CompositeJob job, Visibility visibility) {
        return new VideoPublishRequest(job.getJobUuid(), "재밌었다!", visibility, true);
    }

    private void assertFails(CompositeJob job, ErrorCode expected) {
        assertThatThrownBy(() -> publish(job, Visibility.PUBLIC))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(expected);
    }

    private CompositeJob reload(CompositeJob job) {
        entityManager.flush();
        entityManager.clear();
        return compositeJobRepository.findById(job.getJobSeq()).orElseThrow();
    }

    /** 가공까지 끝난 잡 — {@code ck_composite_jobs_status_shape} 를 지나려면 키가 함께 차야 한다. */
    private CompositeJob doneJob() {
        UUID uuid = UUID.randomUUID();
        CompositeJob job = CompositeJob.create(uuid, JobType.CHALLENGE, fanSeq, challengeSeq,
                null, newSlot(), "raw/" + uuid + ".mp4");
        job.queue(true);
        job.markSubmitted();
        job.complete("private/challenge/" + uuid + "/master.m3u8",
                "private/challenge/" + uuid + "/thumb.jpg");
        return compositeJobRepository.saveAndFlush(job);
    }

    private User newUser() {
        User user = userRepository.saveAndFlush(User.signUp());
        user.applyInitialNickname();
        return user;
    }

    private Integer newArtist() {
        User artistUser = userRepository.saveAndFlush(User.createArtist("카리나"));
        return artistRepository.saveAndFlush(Artist.register(
                artistUser.getUserSeq(), null, newCompany(), null, null, adminUserSeq)).getArtistSeq();
    }

    private Integer newCompany() {
        String name = "t-" + UUID.randomUUID().toString().substring(0, 20);
        entityManager.createNativeQuery(
                        "insert into motif_core.companies (company_name, reg_usr_seq) values (:name, :regUsrSeq)")
                .setParameter("name", name)
                .setParameter("regUsrSeq", adminUserSeq)
                .executeUpdate();
        return ((Number) entityManager.createNativeQuery(
                        "select company_seq from motif_core.companies where company_name = :name")
                .setParameter("name", name)
                .getSingleResult()).intValue();
    }

    private Integer newChallenge() {
        String uuid = UUID.randomUUID().toString();
        Integer songSeq = songRepository.saveAndFlush(
                Song.register("곡 " + uuid.substring(0, 8), "카리나", adminUserSeq)).getSongSeq();
        return challengeRepository.saveAndFlush(Challenge.register(
                        artistSeq, songSeq, "소개", "challenges/" + uuid + "/master.m3u8",
                        "challenges/" + uuid + "/thumb.jpg", 15_000))
                .getChallengeSeq();
    }

    /**
     * 그 챌린지의 슬롯 하나 — 없으면 만든다.
     *
     * <p>CHALLENGE 잡은 언제나 슬롯을 가리킨다({@code ck_composite_jobs_type_shape},
     * changeSet 0021). 한 테스트가 잡을 둘 만들 수 있어 매번 새로 만들면
     * {@code uq_challenge_participants_slot_order} 에 걸린다.
     */
    private Integer newSlot() {
        return participantRepository.findByChallengeSeqOrderBySlotOrderAsc(challengeSeq).stream()
                .findFirst()
                .map(ChallengeParticipant::getChallengeParticipantSeq)
                .orElseGet(() -> participantRepository.saveAndFlush(ChallengeParticipant.of(
                                challengeSeq, artistSeq, 0,
                                new ChallengeParticipant.Anchor(0.22, 0.36, 0.0995)))
                        .getChallengeParticipantSeq());
    }
}
