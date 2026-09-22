package com.ssafy.motif.coreapi.domain.composite.service;

import com.ssafy.motif.coreapi.domain.composite.dto.CompositeJobStatusResponse;
import com.ssafy.motif.coreapi.domain.motif.service.MotifResultQueryService;
import com.ssafy.motif.event.contract.CompositeFinishedEvent;
import com.ssafy.motif.event.contract.CompositePackagedEvent;
import com.ssafy.motif.event.contract.MediaJobRequestedEvent;
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
import com.ssafy.motif.domain.core.motif.MotifResultRepository;
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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 가공 완료 → {@code DONE} 확정과 {@code composite.finished} 발행
 * (S15P21D104-189 · 249 · 318).
 *
 * <p><b>테스트가 바깥에 트랜잭션을 열지 않는다.</b> 검증 대상이 트랜잭션 경계 그
 * 자체이기 때문이다 — ① MOTIF 결과 행 생성이 실패하면 {@code DONE} 까지 롤백되는가
 * ② {@code composite.finished} 가 <b>커밋 후에만</b> 나가는가. 테스트가 트랜잭션을
 * 열면 서비스의 커밋이 커밋이 아니게 되고 {@code AFTER_COMMIT} 리스너는 영영 안 돈다
 * (작업명세 §7 함정 1 — 그 침묵이 이 기능의 대표 사고다). 대신 만든 행을
 * {@link #cleanUp()} 이 역순으로 지운다.
 *
 * <p>MQ 는 목이다 — 발행 여부와 페이로드만 본다. 실제 브로커 왕복은 관리 UI 로 따로 확인한다.
 *
 * <p>실행 전 {@code backend/local-infra && docker compose up -d --wait}.
 */
@Tag("local-db")
@SpringBootTest
class CompositeJobPackagedLocalDbTest {

    @Autowired
    private CompositeJobCompletionService completionService;
    @Autowired
    private CompositeJobQueryService compositeJobQueryService;
    @Autowired
    private CompositeJobRepository compositeJobRepository;
    @Autowired
    private MotifResultRepository motifResultRepository;
    @Autowired
    private MotifResultQueryService motifResultQueryService;
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
    private RabbitTemplate rabbitTemplate;

    // 롤백이 없으니 만든 것을 전부 기억한다 — 로컬 compose DB 를 다른 테스트와 공유하고,
    // 어드민 목록 테스트들은 테이블 전체 건수로 단언한다. 남기면 그쪽이 깨진다
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

    // --- CHALLENGE (189) ---------------------------------------------------------

    @Test
    @DisplayName("packaged → DONE + 산출물 키. 상태와 키가 한 UPDATE 라 CHECK 를 지난다")
    void packagedCompletesChallengeJob() {
        CompositeJob job = inTransaction(() -> processingChallengeJob());

        completionService.packaged(packaged(job, 15_000));

        CompositeJob saved = reload(job.getJobSeq());
        assertThat(saved.getStatus()).isEqualTo(JobStatus.DONE);
        assertThat(saved.getResultVideoKey()).isEqualTo(masterKey(job));
        assertThat(saved.getResultThumbnailKey()).isEqualTo(thumbKey(job));
        assertThat(saved.getFailCode()).isNull();
    }

    @Test
    @DisplayName("packaged 를 두 번 받아도 아무것도 바뀌지 않고 발행도 한 번뿐이다")
    void duplicatePackagedChangesNothing() {
        CompositeJob job = inTransaction(() -> processingChallengeJob());
        completionService.packaged(packaged(job, 15_000));
        Instant firstMod = reload(job.getJobSeq()).getModDttm();

        completionService.packaged(packaged(job, 15_000));

        CompositeJob saved = reload(job.getJobSeq());
        assertThat(saved.getStatus()).isEqualTo(JobStatus.DONE);
        assertThat(saved.getModDttm()).isEqualTo(firstMod);
        assertThat(finishedEvents()).hasSize(1);
    }

    @Test
    @DisplayName("가공 중 챌린지가 삭제됐으면 FAILED(CHALLENGE_DELETED) — DONE 으로 두지 않는다")
    void deletedChallengeFailsTheJob() {
        CompositeJob job = inTransaction(() -> processingChallengeJob());
        inTransaction(() -> {
            challengeRepository.findById(job.getChallengeSeq()).orElseThrow().softDelete();
            return null;
        });

        completionService.packaged(packaged(job, 15_000));

        CompositeJob saved = reload(job.getJobSeq());
        assertThat(saved.getStatus()).isEqualTo(JobStatus.FAILED);
        assertThat(saved.getFailCode())
                .isEqualTo(CompositeJobCompletionService.FAIL_CHALLENGE_DELETED);
        assertThat(saved.getResultVideoKey()).isNull();
        assertThat(finishedEvents()).singleElement()
                .satisfies(e -> {
                    assertThat(e.status()).isEqualTo(CompositeFinishedEvent.STATUS_FAILED);
                    assertThat(e.failCode())
                            .isEqualTo(CompositeJobCompletionService.FAIL_CHALLENGE_DELETED);
                });
    }

    @Test
    @DisplayName("COMPOSITE_RESULT 가 아닌 소스는 잡을 건드리지 않는다")
    void otherSourceTypesAreIgnoredByTheListenerGuard() {
        CompositeJob job = inTransaction(() -> processingChallengeJob());

        // 리스너가 먼저 거르지만, 서비스까지 흘러도 잡을 찾지 못하게 sourceSeq 가 다르다
        completionService.packaged(new CompositePackagedEvent(
                UUID.randomUUID().toString(), Instant.now(), UUID.randomUUID().toString(),
                MediaJobRequestedEvent.SOURCE_CHALLENGE_ORIGIN, -1L,
                "challenges/x", "challenges/x/master.m3u8", "challenges/x/thumb.jpg", 15_000));

        assertThat(reload(job.getJobSeq()).getStatus()).isEqualTo(JobStatus.PROCESSING);
        verify(rabbitTemplate, never()).convertAndSend(
                org.mockito.ArgumentMatchers.anyString(),
                eq(RoutingKeys.COMPOSITE_FINISHED),
                org.mockito.ArgumentMatchers.<Object>any());
    }

    // --- MOTIF (249) -------------------------------------------------------------

    @Test
    @DisplayName("MOTIF 는 결과 행이 먼저 생기고 그 seq 가 finished 와 §13.3 에 함께 실린다")
    void motifCreatesResultRowBeforeDone() {
        MotifAsset asset = inTransaction(this::newAsset);
        CompositeJob job = inTransaction(() -> processingMotifJob(asset));

        completionService.packaged(packaged(job, 9_800));

        Long resultSeq = motifResultRepository.findByJobSeq(job.getJobSeq())
                .orElseThrow().getResultSeq();
        assertThat(reload(job.getJobSeq()).getStatus()).isEqualTo(JobStatus.DONE);
        assertThat(finishedEvents()).singleElement().satisfies(e -> {
            assertThat(e.status()).isEqualTo(CompositeFinishedEvent.STATUS_DONE);
            assertThat(e.resultSeq()).isEqualTo(resultSeq);
            assertThat(e.jobType()).isEqualTo("MOTIF");
            assertThat(e.userSeq()).isEqualTo(fanSeq);
        });
        // §13.3 이 같은 값을 답해야 한다 — 앱이 이 seq 로 §11.6 에 들어간다
        var status = (CompositeJobStatusResponse.Motif)
                compositeJobQueryService.status(fanSeq, job.getJobUuid());
        assertThat(status.resultSeq()).isEqualTo(resultSeq);
    }

    @Test
    @DisplayName("DONE 직후 보관함 목록(§11.5)·상세(§11.6)에 바로 보인다")
    void motifResultIsVisibleInTheLibrary() {
        MotifAsset asset = inTransaction(this::newAsset);
        CompositeJob job = inTransaction(() -> processingMotifJob(asset));

        completionService.packaged(packaged(job, 9_800));

        Long resultSeq = motifResultRepository.findByJobSeq(job.getJobSeq())
                .orElseThrow().getResultSeq();
        assertThat(motifResultQueryService.results(fanSeq, null, null, 20).list())
                .extracting(item -> item.resultSeq())
                .contains(resultSeq);
        assertThat(motifResultQueryService.detail(fanSeq, resultSeq))
                .satisfies(detail -> {
                    assertThat(detail.resultSeq()).isEqualTo(resultSeq);
                    // 문구는 사용자가 §11.8 로 넣는다 — 합성이 끝났다고 채우지 않는다
                    assertThat(detail.description()).isNull();
                });
    }

    @Test
    @DisplayName("packaged 가 두 번 와도 결과 행은 하나다 — uq_motif_results_job")
    void duplicatePackagedKeepsOneResultRow() {
        MotifAsset asset = inTransaction(this::newAsset);
        CompositeJob job = inTransaction(() -> processingMotifJob(asset));

        completionService.packaged(packaged(job, 9_800));
        completionService.packaged(packaged(job, 9_800));

        assertThat(countResults(job.getJobSeq())).isEqualTo(1);
    }

    @Test
    @DisplayName("이벤트에 길이가 없으면 에셋 길이로 갈음한다 — duration_ms 는 NOT NULL 이다")
    void missingDurationFallsBackToTheAsset() {
        MotifAsset asset = inTransaction(this::newAsset);
        CompositeJob job = inTransaction(() -> processingMotifJob(asset));

        completionService.packaged(packaged(job, null));

        assertThat(motifResultRepository.findByJobSeq(job.getJobSeq()).orElseThrow()
                .getDurationMs()).isEqualTo(asset.getDurationMs());
    }

    // --- 318 발행 -------------------------------------------------------------------

    @Test
    @DisplayName("발행은 커밋 후다 — 전이가 끝난 뒤 motif.topic/composite.finished 로 한 건")
    void finishedIsPublishedAfterCommit() {
        CompositeJob job = inTransaction(() -> processingChallengeJob());

        completionService.packaged(packaged(job, 15_000));

        verify(rabbitTemplate).convertAndSend(
                eq("motif.topic"), eq(RoutingKeys.COMPOSITE_FINISHED),
                org.mockito.ArgumentMatchers.<Object>any());
        assertThat(finishedEvents()).singleElement().satisfies(e -> {
            assertThat(e.jobUuid()).isEqualTo(job.getJobUuid().toString());
            assertThat(e.jobType()).isEqualTo("CHALLENGE");
            assertThat(e.status()).isEqualTo(CompositeFinishedEvent.STATUS_DONE);
            assertThat(e.failCode()).isNull();
            assertThat(e.resultSeq()).isNull();
            assertThat(e.eventId()).isNotBlank();
            assertThat(e.occurredAt()).isNotNull();
        });
    }

    // --- 픽스처 -------------------------------------------------------------------------

    private List<CompositeFinishedEvent> finishedEvents() {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(rabbitTemplate, org.mockito.Mockito.atLeast(0)).convertAndSend(
                org.mockito.ArgumentMatchers.anyString(),
                eq(RoutingKeys.COMPOSITE_FINISHED),
                captor.capture());
        return captor.getAllValues().stream()
                .filter(CompositeFinishedEvent.class::isInstance)
                .map(CompositeFinishedEvent.class::cast)
                .toList();
    }

    private CompositePackagedEvent packaged(CompositeJob job, Integer durationMs) {
        return new CompositePackagedEvent(
                UUID.randomUUID().toString(), Instant.now(), UUID.randomUUID().toString(),
                MediaJobRequestedEvent.SOURCE_COMPOSITE_RESULT, job.getJobSeq(),
                prefix(job), masterKey(job), thumbKey(job), durationMs);
    }

    private static String prefix(CompositeJob job) {
        return "private/" + job.getJobType().name().toLowerCase() + "/" + job.getJobUuid();
    }

    private static String masterKey(CompositeJob job) {
        return prefix(job) + "/master.m3u8";
    }

    private static String thumbKey(CompositeJob job) {
        return prefix(job) + "/thumb.jpg";
    }

    private CompositeJob reload(Long jobSeq) {
        return inTransaction(() -> compositeJobRepository.findById(jobSeq).orElseThrow());
    }

    private int countResults(Long jobSeq) {
        return inTransaction(() -> ((Number) entityManager.createNativeQuery(
                        "select count(*) from motif_core.motif_results where job_seq = :jobSeq")
                .setParameter("jobSeq", jobSeq)
                .getSingleResult()).intValue());
    }

    /** 가공 직전 상태 — AI 접수까지 끝난 잡이다. */
    private CompositeJob processingChallengeJob() {
        Integer challengeSeq = newChallenge();
        CompositeJob job = CompositeJob.create(UUID.randomUUID(), JobType.CHALLENGE, fanSeq,
                challengeSeq, null, newSlot(challengeSeq), "raw/" + UUID.randomUUID() + ".mp4");
        job.queue(true);
        job.markSubmitted();
        compositeJobRepository.saveAndFlush(job);
        createdJobs.add(job.getJobSeq());
        return job;
    }

    private CompositeJob processingMotifJob(MotifAsset asset) {
        CompositeJob job = CompositeJob.create(UUID.randomUUID(), JobType.MOTIF, fanSeq,
                null, asset.getAssetSeq(), null, "raw/" + UUID.randomUUID() + ".mp4");
        job.queue(false);
        job.markSubmitted();
        compositeJobRepository.saveAndFlush(job);
        createdJobs.add(job.getJobSeq());
        return job;
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
                        artistSeq, songSeq, "소개",
                        "challenges/" + uuid + "/master.m3u8",
                        "challenges/" + uuid + "/thumb.jpg", 15_000))
                .getChallengeSeq();
        createdChallenges.add(challengeSeq);
        return challengeSeq;
    }

    /**
     * 편집본까지 채운 슬롯 하나 — CHALLENGE 잡은 언제나 슬롯을 가리킨다
     * ({@code ck_composite_jobs_type_shape}, 0021).
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
