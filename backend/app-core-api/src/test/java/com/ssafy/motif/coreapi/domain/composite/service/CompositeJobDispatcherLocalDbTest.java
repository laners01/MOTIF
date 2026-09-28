package com.ssafy.motif.coreapi.domain.composite.service;

import com.ssafy.motif.coreapi.domain.composite.dto.CompositeJobPayload;
import com.ssafy.motif.coreapi.global.media.MediaObjectStore;
import com.ssafy.motif.event.contract.CompositeFinishedEvent;
import com.ssafy.motif.event.topology.RoutingKeys;
import com.ssafy.motif.domain.core.artist.Artist;
import com.ssafy.motif.domain.core.artist.ArtistRepository;
import com.ssafy.motif.domain.core.asset.AssetType;
import com.ssafy.motif.domain.core.asset.MotifAsset;
import com.ssafy.motif.domain.core.asset.MotifAssetRepository;
import com.ssafy.motif.domain.core.challenge.Challenge;
import com.ssafy.motif.domain.core.challenge.ChallengeParticipant;
import com.ssafy.motif.domain.core.challenge.ChallengeParticipantRepository;
import com.ssafy.motif.domain.core.challenge.ChallengeRepository;
import com.ssafy.motif.domain.core.job.CompositeJob;
import com.ssafy.motif.domain.core.job.CompositeJobRepository;
import com.ssafy.motif.domain.core.job.JobStatus;
import com.ssafy.motif.domain.core.job.JobType;
import com.ssafy.motif.domain.core.song.Song;
import com.ssafy.motif.domain.core.song.SongRepository;
import com.ssafy.motif.domain.core.user.User;
import com.ssafy.motif.domain.core.user.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 디스패처 — 접수 실패 복구와 정체 잡 처리 (작업명세 §4.7, S15P21D104-537).
 *
 * <p><b>시간을 기다리지 않는다.</b> 판정 기준이 2분·35분이라 실제로 기다리면 테스트가
 * 돌 수 없다. {@code mod_dttm} 을 네이티브로 과거로 밀고 {@link CompositeJobDispatcher#sweep()}
 * 를 직접 부른다 — 스케줄 등록은 {@code CompositeDispatcherConfig} 의 몫이고 여기서
 * 검증할 것은 <b>무엇을 집어 무엇으로 바꾸는가</b> 다.
 *
 * <p><b>테스트가 바깥에 트랜잭션을 열지 않는다.</b> 디스패처가 잡마다 트랜잭션을 따로
 * 열고, {@code composite.finished} 릴레이가 {@code AFTER_COMMIT} 이다 — 바깥에서 열면
 * 릴레이가 영영 안 돈다(§7 함정 1). 대신 만든 행을 {@link #cleanUp()} 이 지운다.
 *
 * <p>접수 호출기는 목이다. HTTP 계약은 {@code CompositeJobSubmitterTest}(536)가 보고,
 * 여기서는 <b>성공/실패에 따라 상태가 어떻게 가는지</b>만 본다.
 *
 * <p>실행 전 {@code backend/local-infra && docker compose up -d --wait}.
 */
@Tag("local-db")
@SpringBootTest
class CompositeJobDispatcherLocalDbTest {

    @Autowired
    private CompositeJobDispatcher dispatcher;
    @Autowired
    private CompositeJobRepository compositeJobRepository;
    @Autowired
    private ChallengeRepository challengeRepository;
    @Autowired
    private ChallengeParticipantRepository participantRepository;
    @Autowired
    private MotifAssetRepository motifAssetRepository;
    @Autowired
    private ArtistRepository artistRepository;
    @Autowired
    private SongRepository songRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @PersistenceContext
    private EntityManager entityManager;

    @MockitoBean
    private CompositeJobSubmitter submitter;
    @MockitoBean
    private MediaObjectStore objectStore;
    @MockitoBean
    private RabbitTemplate rabbitTemplate;

    private final List<Long> createdJobs = new ArrayList<>();
    private final List<Integer> createdChallenges = new ArrayList<>();
    private final List<Integer> createdSongs = new ArrayList<>();
    private final List<Integer> createdAssets = new ArrayList<>();
    private final List<Integer> createdArtists = new ArrayList<>();
    private final List<Integer> createdCompanies = new ArrayList<>();
    private final List<Integer> createdUsers = new ArrayList<>();

    private Integer adminUserSeq;
    private Integer fanSeq;
    private Integer artistSeq;

    @BeforeEach
    void setUp() {
        adminUserSeq = inTransaction(() -> newUser().getUserSeq());
        fanSeq = inTransaction(() -> newUser().getUserSeq());
        artistSeq = inTransaction(this::newArtist);
    }

    // --- ① QUEUED 재접수 ------------------------------------------------------------

    @Test
    @DisplayName("접수 안 된 QUEUED 를 다시 민다 — attempt 가 오르고 상태는 그대로")
    void resubmitsStuckQueued() {
        CompositeJob job = inTransaction(() -> queuedJob(1));
        backdate(job.getJobSeq(), "3 minutes");
        given(submitter.submit(any())).willReturn(false);   // 접수 또 실패

        dispatcher.sweep();

        CompositeJob saved = reload(job.getJobSeq());
        assertThat(saved.getStatus()).isEqualTo(JobStatus.QUEUED);
        assertThat(saved.getAttempt()).isEqualTo((short) 2);
        ArgumentCaptor<CompositeJobPayload> payload =
                ArgumentCaptor.forClass(CompositeJobPayload.class);
        verify(submitter).submit(payload.capture());
        // 올라간 attempt 가 페이로드에 실린다 — AI 로그 대조용이다
        assertThat(payload.getValue().attempt()).isEqualTo((short) 2);
        assertThat(payload.getValue().jobUuid()).isEqualTo(job.getJobUuid());
    }

    @Test
    @DisplayName("상한까지 접수가 실패하면 STUCK 으로 닫고 finished(FAILED) 를 낸다 — 무한 루프를 막는다")
    void repeatedSubmitFailureEndsAsStuck() {
        CompositeJob job = inTransaction(() -> queuedJob(3));
        backdate(job.getJobSeq(), "3 minutes");
        given(submitter.submit(any())).willReturn(false);

        dispatcher.sweep();

        CompositeJob saved = reload(job.getJobSeq());
        assertThat(saved.getStatus()).isEqualTo(JobStatus.FAILED);
        assertThat(saved.getFailCode()).isEqualTo(CompositeJobDispatcher.FAIL_STUCK);
        // 닫기 전에 한 번은 보내 본다 — 검사가 접수 앞에 있으면 §13.4 가 막힌다
        verify(submitter).submit(any());
        assertThat(finishedEvents()).singleElement().satisfies(e -> {
            assertThat(e.status()).isEqualTo(CompositeFinishedEvent.STATUS_FAILED);
            assertThat(e.failCode()).isEqualTo(CompositeJobDispatcher.FAIL_STUCK);
        });
    }

    @Test
    @DisplayName("§13.4 재시도로 attempt 가 상한에 닿아도 접수는 해 본다 — 되면 STUCK 이 아니다")
    void manualRetryIsStillSubmitted() {
        // 사용자가 "다시 시도" 를 눌러 attempt 가 상한까지 올라온 잡이다.
        // 접수 전에 상한을 보면 AI 에 한 번도 못 가 보고 닫혀 버튼이 먹통이 된다
        CompositeJob job = inTransaction(() -> queuedJob(3));
        backdate(job.getJobSeq(), "3 minutes");
        given(submitter.submit(any())).willReturn(true);

        dispatcher.sweep();

        verify(submitter).submit(any());
        CompositeJob saved = reload(job.getJobSeq());
        assertThat(saved.getStatus()).isNotEqualTo(JobStatus.FAILED);
        assertThat(saved.getFailCode()).isNull();
        assertThat(saved.getAttempt()).isEqualTo((short) 4);
        assertThat(finishedEvents()).isEmpty();
    }

    @Test
    @DisplayName("방금 만든 QUEUED 는 건드리지 않는다 — 13.2 직후 중복 접수를 막는 간격")
    void freshQueuedIsLeftAlone() {
        CompositeJob job = inTransaction(() -> queuedJob(1));

        dispatcher.sweep();

        assertThat(reload(job.getJobSeq()).getAttempt()).isEqualTo((short) 1);
        verify(submitter, never()).submit(any());
    }

    @Test
    @DisplayName("유료가 먼저다 — 큐 우선순위가 사라진 자리를 정렬이 메운다")
    void motifGoesFirst() {
        MotifAsset asset = inTransaction(this::newAsset);
        CompositeJob challenge = inTransaction(() -> queuedJob(1));
        CompositeJob motif = inTransaction(() -> queuedMotifJob(asset));
        backdate(challenge.getJobSeq(), "3 minutes");
        backdate(motif.getJobSeq(), "3 minutes");
        given(submitter.submit(any())).willReturn(false);

        dispatcher.sweep();

        ArgumentCaptor<CompositeJobPayload> payloads =
                ArgumentCaptor.forClass(CompositeJobPayload.class);
        verify(submitter, org.mockito.Mockito.times(2)).submit(payloads.capture());
        assertThat(payloads.getAllValues()).extracting(CompositeJobPayload::jobType)
                .containsExactly("MOTIF", "CHALLENGE");
    }

    // --- ② PROCESSING 정체 ----------------------------------------------------------

    @Test
    @DisplayName("S3 에 결과가 있으면 콜백 유실로 보고 살린다 — 결과물을 버리지 않는다")
    void recoversProcessingWhenResultExists() {
        CompositeJob job = inTransaction(() -> processingJob());
        backdate(job.getJobSeq(), "40 minutes");
        String resultKey = "results/challenge/" + job.getJobUuid() + ".mp4";
        given(objectStore.exists(resultKey)).willReturn(true);

        dispatcher.sweep();

        // /done 과 같은 경로다 — 상태는 PROCESSING 그대로이고 job.media 가 나간다.
        // DONE 확정은 composite.packaged 가 한다(189)
        CompositeJob saved = reload(job.getJobSeq());
        assertThat(saved.getStatus()).isEqualTo(JobStatus.PROCESSING);
        assertThat(saved.getFailCode()).isNull();
        verify(rabbitTemplate).convertAndSend(anyString(), eq(RoutingKeys.JOB_MEDIA),
                org.mockito.ArgumentMatchers.<Object>any());
    }

    @Test
    @DisplayName("결과도 없으면 STUCK — task-timeout + 5분을 넘긴 잡이다")
    void failsProcessingWhenNoResult() {
        CompositeJob job = inTransaction(() -> processingJob());
        backdate(job.getJobSeq(), "40 minutes");
        given(objectStore.exists(anyString())).willReturn(false);

        dispatcher.sweep();

        CompositeJob saved = reload(job.getJobSeq());
        assertThat(saved.getStatus()).isEqualTo(JobStatus.FAILED);
        assertThat(saved.getFailCode()).isEqualTo(CompositeJobDispatcher.FAIL_STUCK);
        assertThat(finishedEvents()).singleElement()
                .satisfies(e -> assertThat(e.status())
                        .isEqualTo(CompositeFinishedEvent.STATUS_FAILED));
    }

    @Test
    @DisplayName("아직 시간이 안 된 PROCESSING 은 건드리지 않는다 — 정상 잡을 닫으면 푸시가 두 번 간다")
    void freshProcessingIsLeftAlone() {
        CompositeJob job = inTransaction(() -> processingJob());
        backdate(job.getJobSeq(), "10 minutes");   // task-timeout(30m)+5m 미만

        dispatcher.sweep();

        assertThat(reload(job.getJobSeq()).getStatus()).isEqualTo(JobStatus.PROCESSING);
        verify(objectStore, never()).exists(anyString());
    }

    // --- 픽스처 -------------------------------------------------------------------------

    private List<CompositeFinishedEvent> finishedEvents() {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(rabbitTemplate, org.mockito.Mockito.atLeast(0)).convertAndSend(
                anyString(), eq(RoutingKeys.COMPOSITE_FINISHED), captor.capture());
        return captor.getAllValues().stream()
                .filter(CompositeFinishedEvent.class::isInstance)
                .map(CompositeFinishedEvent.class::cast)
                .toList();
    }

    /** {@code mod_dttm} 을 과거로 민다 — 정체 판정이 COALESCE(mod, reg) 기준이다. */
    private void backdate(Long jobSeq, String interval) {
        inTransaction(() -> {
            entityManager.createNativeQuery(
                            "update motif_core.composite_jobs set mod_dttm = now() - cast(:iv as interval)"
                                    + " where job_seq = :seq")
                    .setParameter("iv", interval)
                    .setParameter("seq", jobSeq)
                    .executeUpdate();
            return null;
        });
    }

    private CompositeJob reload(Long jobSeq) {
        return inTransaction(() -> compositeJobRepository.findById(jobSeq).orElseThrow());
    }

    private CompositeJob queuedJob(int attempt) {
        Integer challengeSeq = newChallenge();
        CompositeJob job = CompositeJob.create(UUID.randomUUID(), JobType.CHALLENGE, fanSeq,
                challengeSeq, null, newSlot(challengeSeq), "raw/" + UUID.randomUUID() + ".mp4");
        job.queue(true);
        compositeJobRepository.saveAndFlush(job);
        bumpAttemptTo(job.getJobSeq(), attempt);
        createdJobs.add(job.getJobSeq());
        return job;
    }

    private CompositeJob queuedMotifJob(MotifAsset asset) {
        CompositeJob job = CompositeJob.create(UUID.randomUUID(), JobType.MOTIF, fanSeq,
                null, asset.getAssetSeq(), null, "raw/" + UUID.randomUUID() + ".mp4");
        job.queue(false);
        compositeJobRepository.saveAndFlush(job);
        createdJobs.add(job.getJobSeq());
        return job;
    }

    private CompositeJob processingJob() {
        Integer challengeSeq = newChallenge();
        CompositeJob job = CompositeJob.create(UUID.randomUUID(), JobType.CHALLENGE, fanSeq,
                challengeSeq, null, newSlot(challengeSeq), "raw/" + UUID.randomUUID() + ".mp4");
        job.queue(true);
        job.markSubmitted();
        compositeJobRepository.saveAndFlush(job);
        createdJobs.add(job.getJobSeq());
        return job;
    }

    /** {@code attempt} 를 바로 세팅할 경로가 없다 — 재시도 횟수는 전이만 올린다. */
    private void bumpAttemptTo(Long jobSeq, int attempt) {
        entityManager.createNativeQuery(
                        "update motif_core.composite_jobs set attempt = :a where job_seq = :seq")
                .setParameter("a", (short) attempt)
                .setParameter("seq", jobSeq)
                .executeUpdate();
        entityManager.clear();
    }

    private User newUser() {
        User user = userRepository.saveAndFlush(User.signUp());
        user.applyInitialNickname();
        createdUsers.add(user.getUserSeq());
        return user;
    }

    private Integer newArtist() {
        User artistUser = userRepository.saveAndFlush(User.createArtist("카리나"));
        createdUsers.add(artistUser.getUserSeq());
        Integer seq = artistRepository.saveAndFlush(Artist.register(
                artistUser.getUserSeq(), null, newCompany(), null, null, adminUserSeq)).getArtistSeq();
        createdArtists.add(seq);
        return seq;
    }

    private Integer newCompany() {
        String name = "t-" + UUID.randomUUID().toString().substring(0, 20);
        entityManager.createNativeQuery(
                        "insert into motif_core.companies (company_name, reg_usr_seq) values (:name, :regUsrSeq)")
                .setParameter("name", name)
                .setParameter("regUsrSeq", adminUserSeq)
                .executeUpdate();
        Integer companySeq = ((Number) entityManager.createNativeQuery(
                        "select company_seq from motif_core.companies where company_name = :name")
                .setParameter("name", name)
                .getSingleResult()).intValue();
        createdCompanies.add(companySeq);
        return companySeq;
    }

    private Integer newChallenge() {
        String uuid = UUID.randomUUID().toString();
        Integer songSeq = songRepository.saveAndFlush(
                Song.register("곡 " + uuid.substring(0, 8), "카리나", adminUserSeq)).getSongSeq();
        createdSongs.add(songSeq);
        Integer challengeSeq = challengeRepository.saveAndFlush(Challenge.register(
                        artistSeq, songSeq, "소개", "challenges/" + uuid + "/master.m3u8",
                        "challenges/" + uuid + "/thumb.jpg", 15_000))
                .getChallengeSeq();
        createdChallenges.add(challengeSeq);
        return challengeSeq;
    }

    /**
     * 편집본까지 채운 슬롯 하나 — CHALLENGE 잡은 언제나 슬롯을 가리킨다
     * ({@code ck_composite_jobs_type_shape}, 0021). 디스패처가 이 슬롯의 편집본 키로
     * 접수 페이로드를 조립하므로 키도 채워야 한다.
     */
    private Integer newSlot(Integer challengeSeq) {
        ChallengeParticipant slot = participantRepository.saveAndFlush(ChallengeParticipant.of(
                challengeSeq, artistSeq, 0,
                new ChallengeParticipant.Anchor(0.22, 0.36, 0.0995)));
        String prefix = "private/edited/" + challengeSeq + "/"
                + slot.getChallengeParticipantSeq() + "/";
        slot.replaceEditedKeys(prefix + UUID.randomUUID() + ".mp4",
                prefix + UUID.randomUUID() + ".jpg", null, 15_000);
        return participantRepository.saveAndFlush(slot).getChallengeParticipantSeq();
    }

    private MotifAsset newAsset() {
        String uuid = UUID.randomUUID().toString();
        MotifAsset asset = motifAssetRepository.saveAndFlush(MotifAsset.register(
                artistSeq, "에셋 " + uuid.substring(0, 8), "소개",
                "assets/" + uuid + ".mp4", "assets/" + uuid + "/thumb.jpg", 9_800, AssetType.NORMAL));
        createdAssets.add(asset.getAssetSeq());
        return asset;
    }

    @AfterEach
    void cleanUp() {
        inTransaction(() -> {
            createdJobs.forEach(seq ->
                    execute("delete from motif_core.motif_results where job_seq = ?1", seq));
            createdJobs.forEach(seq ->
                    execute("delete from motif_core.composite_jobs where job_seq = ?1", seq));
            // 슬롯은 챌린지보다 먼저 — FK 가 ON DELETE RESTRICT 다
            createdChallenges.forEach(seq -> execute(
                    "delete from motif_core.challenge_participants where challenge_seq = ?1", seq));
            createdChallenges.forEach(seq ->
                    execute("delete from motif_core.challenges where challenge_seq = ?1", seq));
            createdSongs.forEach(seq ->
                    execute("delete from motif_core.songs where song_seq = ?1", seq));
            createdAssets.forEach(seq ->
                    execute("delete from motif_core.motif_assets where asset_seq = ?1", seq));
            createdArtists.forEach(seq ->
                    execute("delete from motif_core.artists where artist_seq = ?1", seq));
            createdCompanies.forEach(seq ->
                    execute("delete from motif_core.companies where company_seq = ?1", seq));
            createdUsers.forEach(seq ->
                    execute("delete from motif_core.users where user_seq = ?1", seq));
            return null;
        });
        createdJobs.clear();
        createdChallenges.clear();
        createdSongs.clear();
        createdAssets.clear();
        createdArtists.clear();
        createdCompanies.clear();
        createdUsers.clear();
    }

    private void execute(String sql, Object param) {
        entityManager.createNativeQuery(sql).setParameter(1, param).executeUpdate();
    }

    private <T> T inTransaction(Supplier<T> work) {
        return transactionTemplate.execute(status -> work.get());
    }
}
