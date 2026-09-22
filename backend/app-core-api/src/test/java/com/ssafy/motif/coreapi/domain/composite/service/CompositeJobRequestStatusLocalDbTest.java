package com.ssafy.motif.coreapi.domain.composite.service;

import com.ssafy.motif.common.error.ErrorCode;
import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.coreapi.domain.composite.CompositeErrorCode;
import com.ssafy.motif.coreapi.domain.composite.dto.CompositeJobRequestBody;
import com.ssafy.motif.coreapi.domain.composite.dto.CompositeJobRetryResponse;
import com.ssafy.motif.coreapi.domain.composite.dto.CompositeJobStatusResponse;
import com.ssafy.motif.coreapi.global.media.MediaObjectStore;
import com.ssafy.motif.coreapi.global.media.MediaUrlResolver;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

/**
 * §13.2 합성 요청 · §13.3 상태 조회 · §13.4 재시도를 실제 DB 에 대고 검증한다
 * (S15P21D104-187 · 247 · 191).
 *
 * <p><b>DB 가 있어야 하는 이유는 상태 모양 때문이다.</b>
 * {@code ck_composite_jobs_status_shape} 가 "{@code DONE} 이면 산출물 키 NOT NULL,
 * {@code FAILED} 면 {@code fail_code} NOT NULL, 나머지는 셋 다 NULL" 을 강제한다 —
 * §13.3 의 세 모양(DONE·진행 중·실패)이 실제로 존재할 수 있는 행인지는 여기서만 드러난다.
 *
 * <p><b>S3 와 URL 리졸버는 목이다.</b> HEAD 는 자격증명이 필요하고, 보호 등급 서명은
 * CloudFront 키가 필요하다(로컬엔 없다). 이 테스트가 볼 것은 <b>어떤 키를 어느 리졸버에
 * 넘기나</b> 지 서명 문자열이 아니다 — 재생목록과 썸네일은 서명 방식이 다르다(ADR-0012).
 *
 * <p>실행 전 {@code backend/local-infra && docker compose up -d --wait}.
 */
@Tag("local-db")
@SpringBootTest
@Transactional
class CompositeJobRequestStatusLocalDbTest {

    private static final String PLAYLIST_URL = "https://cdn.test/private/challenge/x/master.m3u8?Policy=stub";
    private static final String THUMB_URL = "https://cdn.test/private/challenge/x/thumb.jpg?Policy=stub";

    @Autowired
    private CompositeJobCommandService compositeJobCommandService;
    @Autowired
    private CompositeJobQueryService compositeJobQueryService;
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
    @PersistenceContext
    private EntityManager entityManager;

    @MockitoBean
    private MediaObjectStore objectStore;
    @MockitoBean
    private MediaUrlResolver mediaUrlResolver;

    private Integer adminUserSeq;
    private Integer fanSeq;
    private Integer artistSeq;

    @BeforeEach
    void setUp() {
        given(objectStore.exists(anyString())).willReturn(true);
        given(mediaUrlResolver.resolvePlaylist(anyString())).willReturn(PLAYLIST_URL);
        given(mediaUrlResolver.resolve(anyString())).willReturn(THUMB_URL);
        adminUserSeq = newUser().getUserSeq();
        fanSeq = newUser().getUserSeq();
        artistSeq = newArtist();
    }

    // --- 13.2 합성 요청 -------------------------------------------------------------

    @Test
    @DisplayName("CHALLENGE — 202 QUEUED 로 전이하고 본문의 correctionEnabled 를 저장한다")
    void challengeRequestQueuesAndStoresCorrection() {
        CompositeJob job = challengeJob();

        var response = compositeJobCommandService.request(
                fanSeq, job.getJobUuid(), new CompositeJobRequestBody(true));

        assertThat(response.jobUuid()).isEqualTo(job.getJobUuid());
        assertThat(response.status()).isEqualTo(JobStatus.QUEUED);
        CompositeJob saved = reload(job.getJobUuid());
        assertThat(saved.getStatus()).isEqualTo(JobStatus.QUEUED);
        assertThat(saved.isCorrectionEnabled()).isTrue();
    }

    @Test
    @DisplayName("CHALLENGE — correctionEnabled 생략은 false 다(기본안). 본문 자체가 없어도 같다")
    void omittedCorrectionIsFalse() {
        assertThat(reload(queued(challengeJob(), new CompositeJobRequestBody(null)))
                .isCorrectionEnabled()).isFalse();
        assertThat(reload(queued(challengeJob(), null)).isCorrectionEnabled()).isFalse();
    }

    @Test
    @DisplayName("MOTIF — correctionEnabled=true 가 와도 무시하고 false 로 둔다")
    void motifIgnoresCorrectionEnabled() {
        CompositeJob job = motifJob();

        var response = compositeJobCommandService.request(
                fanSeq, job.getJobUuid(), new CompositeJobRequestBody(true));

        assertThat(response.status()).isEqualTo(JobStatus.QUEUED);
        assertThat(reload(job.getJobUuid()).isCorrectionEnabled()).isFalse();
    }

    @Test
    @DisplayName("S3 에 원본이 없으면 409 — 업로드 미완료와 URL 만료가 같은 코드다")
    void missingRawIsConflict() {
        given(objectStore.exists(anyString())).willReturn(false);

        assertFails(challengeJob().getJobUuid(), null, CompositeErrorCode.RAW_NOT_UPLOADED);
    }

    @Test
    @DisplayName("두 번째 요청은 409 — 상태가 이미 UPLOADED 가 아니다")
    void duplicateRequestIsConflict() {
        CompositeJob job = challengeJob();
        queued(job, new CompositeJobRequestBody(false));

        assertFails(job.getJobUuid(), null, CompositeErrorCode.JOB_NOT_REQUESTABLE);
    }

    @Test
    @DisplayName("타인 잡은 404 — 403 으로 가르면 존재가 읽힌다")
    void othersJobIsNotFoundOnRequest() {
        CompositeJob job = challengeJob();
        Integer stranger = newUser().getUserSeq();

        assertThatThrownBy(() -> compositeJobCommandService.request(stranger, job.getJobUuid(), null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CompositeErrorCode.JOB_NOT_FOUND);
    }

    @Test
    @DisplayName("없는 jobUuid 도 같은 404 다")
    void unknownJobIsNotFound() {
        assertFails(UUID.randomUUID(), null, CompositeErrorCode.JOB_NOT_FOUND);
    }

    // --- 13.3 상태 조회 -------------------------------------------------------------

    @Test
    @DisplayName("CHALLENGE DONE — 재생목록과 썸네일이 서로 다른 리졸버를 탄다 (ADR-0012)")
    void challengeDoneCarriesBothUrls() {
        CompositeJob job = challengeJob();
        markDone(job.getJobUuid(), "private/challenge/" + job.getJobUuid() + "/master.m3u8",
                "private/challenge/" + job.getJobUuid() + "/thumb.jpg");

        var status = (CompositeJobStatusResponse.Challenge) status(job.getJobUuid());

        assertThat(status.jobType()).isEqualTo(JobType.CHALLENGE);
        assertThat(status.status()).isEqualTo(JobStatus.DONE);
        assertThat(status.attempt()).isEqualTo((short) 1);
        assertThat(status.failCode()).isNull();
        assertThat(status.resultVideoUrl()).isEqualTo(PLAYLIST_URL);
        assertThat(status.resultThumbnailUrl()).isEqualTo(THUMB_URL);
    }

    @Test
    @DisplayName("진행 중이면 분기 필드가 전부 null — QUEUED·PROCESSING 이 같은 모양이다")
    void inProgressHasNullBranchFields() {
        CompositeJob job = challengeJob();
        queued(job, null);

        var queuedStatus = (CompositeJobStatusResponse.Challenge) status(job.getJobUuid());
        assertThat(queuedStatus.status()).isEqualTo(JobStatus.QUEUED);
        assertThat(queuedStatus.resultVideoUrl()).isNull();
        assertThat(queuedStatus.resultThumbnailUrl()).isNull();

        setStatus(job.getJobUuid(), "PROCESSING");
        var processing = (CompositeJobStatusResponse.Challenge) status(job.getJobUuid());
        assertThat(processing.status()).isEqualTo(JobStatus.PROCESSING);
        assertThat(processing.resultVideoUrl()).isNull();
        assertThat(processing.failCode()).isNull();
    }

    @Test
    @DisplayName("실패한 잡도 조회는 200 이다 — failCode 로 안내하고 분기 필드는 null")
    void failedJobStillReads() {
        CompositeJob job = challengeJob();
        markFailed(job.getJobUuid(), "FACE_NOT_DETECTED");

        var status = (CompositeJobStatusResponse.Challenge) status(job.getJobUuid());

        assertThat(status.status()).isEqualTo(JobStatus.FAILED);
        assertThat(status.failCode()).isEqualTo("FACE_NOT_DETECTED");
        assertThat(status.resultVideoUrl()).isNull();
        assertThat(status.resultThumbnailUrl()).isNull();
    }

    @Test
    @DisplayName("MOTIF DONE — URL 이 없고 resultSeq 로 보관함 상세를 가리킨다")
    void motifDoneCarriesResultSeq() {
        CompositeJob job = motifJob();
        markDone(job.getJobUuid(), "private/motif/" + job.getJobUuid() + "/master.m3u8",
                "private/motif/" + job.getJobUuid() + "/thumb.jpg");
        long resultSeq = newMotifResult(reload(job.getJobUuid()).getJobSeq(), job.getAssetSeq());

        var status = (CompositeJobStatusResponse.Motif) status(job.getJobUuid());

        assertThat(status.jobType()).isEqualTo(JobType.MOTIF);
        assertThat(status.status()).isEqualTo(JobStatus.DONE);
        assertThat(status.resultSeq()).isEqualTo(resultSeq);
    }

    @Test
    @DisplayName("MOTIF 진행 중이면 resultSeq 가 null 이다")
    void motifInProgressHasNullResultSeq() {
        CompositeJob job = motifJob();

        var status = (CompositeJobStatusResponse.Motif) status(job.getJobUuid());

        assertThat(status.status()).isEqualTo(JobStatus.UPLOADED);
        assertThat(status.resultSeq()).isNull();
    }

    @Test
    @DisplayName("조회도 타인 잡은 404 다")
    void othersJobIsNotFoundOnStatus() {
        CompositeJob job = challengeJob();
        Integer stranger = newUser().getUserSeq();

        assertThatThrownBy(() -> compositeJobQueryService.status(stranger, job.getJobUuid()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CompositeErrorCode.JOB_NOT_FOUND);
    }

    // --- 13.4 재시도 (191) ------------------------------------------------------------

    @Test
    @DisplayName("FAILED → 202 QUEUED, attempt+1, fail_code 초기화. jobUuid 는 그대로다")
    void retryRequeuesTheSameRow() {
        CompositeJob job = challengeJob();
        markFailed(job.getJobUuid(), "FACE_NOT_DETECTED");

        CompositeJobRetryResponse response =
                compositeJobCommandService.retry(fanSeq, job.getJobUuid());

        assertThat(response.jobUuid()).isEqualTo(job.getJobUuid());
        assertThat(response.status()).isEqualTo(JobStatus.QUEUED);
        assertThat(response.attempt()).isEqualTo((short) 2);
        CompositeJob saved = reload(job.getJobUuid());
        assertThat(saved.getStatus()).isEqualTo(JobStatus.QUEUED);
        assertThat(saved.getAttempt()).isEqualTo((short) 2);
        assertThat(saved.getFailCode()).isNull();
        // 새 잡을 만들지 않았다 — 같은 행이다
        assertThat(saved.getJobSeq()).isEqualTo(reload(job.getJobUuid()).getJobSeq());
    }

    @Test
    @DisplayName("MOTIF 도 같은 경로다 — 재시도에 타입별 분기가 없다")
    void retryWorksForMotif() {
        CompositeJob job = motifJob();
        markFailed(job.getJobUuid(), "TIMEOUT");

        assertThat(compositeJobCommandService.retry(fanSeq, job.getJobUuid()).status())
                .isEqualTo(JobStatus.QUEUED);
        assertThat(reload(job.getJobUuid()).getStatus()).isEqualTo(JobStatus.QUEUED);
    }

    @Test
    @DisplayName("FAILED 가 아니면 409 — DONE·QUEUED·PROCESSING 전부 같은 코드다")
    void retryOnNonFailedIsConflict() {
        CompositeJob done = challengeJob();
        markDone(done.getJobUuid(), "private/challenge/" + done.getJobUuid() + "/master.m3u8",
                "private/challenge/" + done.getJobUuid() + "/thumb.jpg");
        assertRetryFails(done.getJobUuid(), CompositeErrorCode.JOB_NOT_RETRYABLE);

        CompositeJob queued = challengeJob();
        queued(queued, null);
        assertRetryFails(queued.getJobUuid(), CompositeErrorCode.JOB_NOT_RETRYABLE);

        CompositeJob processing = challengeJob();
        setStatus(processing.getJobUuid(), "PROCESSING");
        assertRetryFails(processing.getJobUuid(), CompositeErrorCode.JOB_NOT_RETRYABLE);
    }

    @Test
    @DisplayName("원본이 지워졌으면 410 — 상태는 FAILED 그대로 둔다")
    void retryWithoutRawIsGone() {
        CompositeJob job = challengeJob();
        markFailed(job.getJobUuid(), "FACE_NOT_DETECTED");
        given(objectStore.exists(anyString())).willReturn(false);

        assertRetryFails(job.getJobUuid(), CompositeErrorCode.RAW_VIDEO_EXPIRED);

        CompositeJob saved = reload(job.getJobUuid());
        assertThat(saved.getStatus()).isEqualTo(JobStatus.FAILED);
        assertThat(saved.getAttempt()).isEqualTo((short) 1);
    }

    @Test
    @DisplayName("상태를 원본보다 먼저 본다 — 성공한 잡의 원본이 만료돼도 410 이 아니라 409 다")
    void statusIsCheckedBeforeRaw() {
        CompositeJob job = challengeJob();
        markDone(job.getJobUuid(), "private/challenge/" + job.getJobUuid() + "/master.m3u8",
                "private/challenge/" + job.getJobUuid() + "/thumb.jpg");
        given(objectStore.exists(anyString())).willReturn(false);   // 원본은 이미 만료

        // 410 을 주면 "다시 촬영하라" 는 엉뚱한 안내가 된다 — 이미 성공한 잡이다
        assertRetryFails(job.getJobUuid(), CompositeErrorCode.JOB_NOT_RETRYABLE);
    }

    @Test
    @DisplayName("타인 잡은 404 — 재시도도 존재를 숨긴다")
    void othersJobIsNotFoundOnRetry() {
        CompositeJob job = challengeJob();
        markFailed(job.getJobUuid(), "FACE_NOT_DETECTED");
        Integer stranger = newUser().getUserSeq();

        assertThatThrownBy(() -> compositeJobCommandService.retry(stranger, job.getJobUuid()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CompositeErrorCode.JOB_NOT_FOUND);
    }

    @Test
    @DisplayName("수동 재시도에는 상한이 없다 — 디스패처의 attempt<3 과 별개다")
    void manualRetryHasNoCap() {
        CompositeJob job = challengeJob();
        for (int i = 2; i <= 5; i++) {
            markFailed(job.getJobUuid(), "FACE_NOT_DETECTED");
            assertThat(compositeJobCommandService.retry(fanSeq, job.getJobUuid()).attempt())
                    .isEqualTo((short) i);
        }
    }

    private void assertRetryFails(UUID jobUuid, ErrorCode expected) {
        assertThatThrownBy(() -> compositeJobCommandService.retry(fanSeq, jobUuid))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(expected);
    }

    // --- 픽스처 -------------------------------------------------------------------------

    private CompositeJobStatusResponse status(UUID jobUuid) {
        return compositeJobQueryService.status(fanSeq, jobUuid);
    }

    private UUID queued(CompositeJob job, CompositeJobRequestBody body) {
        compositeJobCommandService.request(fanSeq, job.getJobUuid(), body);
        return job.getJobUuid();
    }

    private void assertFails(UUID jobUuid, CompositeJobRequestBody body, ErrorCode expected) {
        assertThatThrownBy(() -> compositeJobCommandService.request(fanSeq, jobUuid, body))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(expected);
    }

    private CompositeJob reload(UUID jobUuid) {
        entityManager.flush();
        entityManager.clear();
        return compositeJobRepository.findByJobUuid(jobUuid).orElseThrow();
    }

    private CompositeJob challengeJob() {
        UUID uuid = UUID.randomUUID();
        Integer challengeSeq = newChallenge();
        return compositeJobRepository.saveAndFlush(CompositeJob.create(
                uuid, JobType.CHALLENGE, fanSeq, challengeSeq, null, newSlot(challengeSeq),
                "raw/" + uuid + ".mp4"));
    }

    /**
     * 슬롯 하나 — CHALLENGE 잡은 언제나 슬롯을 가리킨다
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

    private CompositeJob motifJob() {
        UUID uuid = UUID.randomUUID();
        return compositeJobRepository.saveAndFlush(CompositeJob.create(
                uuid, JobType.MOTIF, fanSeq, null, newAsset().getAssetSeq(), null,
                "raw/" + uuid + ".mp4"));
    }

    /** {@code ck_composite_jobs_status_shape} 를 지나는 유일한 모양 — 상태와 키를 한 UPDATE 로. */
    private void markDone(UUID jobUuid, String masterKey, String thumbKey) {
        entityManager.flush();
        entityManager.createNativeQuery("""
                        update motif_core.composite_jobs
                           set status = 'DONE', result_video_key = :master,
                               result_thumbnail_key = :thumb, fail_code = null
                         where job_uuid = cast(:jobUuid as uuid)
                        """)
                .setParameter("master", masterKey)
                .setParameter("thumb", thumbKey)
                .setParameter("jobUuid", jobUuid.toString())
                .executeUpdate();
        entityManager.clear();
    }

    private void markFailed(UUID jobUuid, String failCode) {
        entityManager.flush();
        entityManager.createNativeQuery("""
                        update motif_core.composite_jobs
                           set status = 'FAILED', fail_code = :failCode,
                               result_video_key = null, result_thumbnail_key = null
                         where job_uuid = cast(:jobUuid as uuid)
                        """)
                .setParameter("failCode", failCode)
                .setParameter("jobUuid", jobUuid.toString())
                .executeUpdate();
        entityManager.clear();
    }

    private void setStatus(UUID jobUuid, String status) {
        entityManager.flush();
        entityManager.createNativeQuery(
                        "update motif_core.composite_jobs set status = :status where job_uuid = cast(:jobUuid as uuid)")
                .setParameter("status", status)
                .setParameter("jobUuid", jobUuid.toString())
                .executeUpdate();
        entityManager.clear();
    }

    private long newMotifResult(Long jobSeq, Integer assetSeq) {
        entityManager.createNativeQuery("""
                        insert into motif_core.motif_results
                               (user_seq, asset_seq, artist_seq, job_seq, video_key, thumbnail_key, duration_ms)
                        values (:userSeq, :assetSeq, :artistSeq, :jobSeq, :videoKey, :thumbKey, 9800)
                        """)
                .setParameter("userSeq", fanSeq)
                .setParameter("assetSeq", assetSeq)
                .setParameter("artistSeq", artistSeq)
                .setParameter("jobSeq", jobSeq)
                .setParameter("videoKey", "private/motif/" + jobSeq + "/master.m3u8")
                .setParameter("thumbKey", "private/motif/" + jobSeq + "/thumb.jpg")
                .executeUpdate();
        entityManager.clear();
        return ((Number) entityManager.createNativeQuery(
                        "select result_seq from motif_core.motif_results where job_seq = :jobSeq")
                .setParameter("jobSeq", jobSeq)
                .getSingleResult()).longValue();
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
                        artistSeq, songSeq, "소개",
                        "challenges/" + uuid + "/master.m3u8",
                        "challenges/" + uuid + "/thumb.jpg", 15_000))
                .getChallengeSeq();
    }

    private MotifAsset newAsset() {
        String uuid = UUID.randomUUID().toString();
        return motifAssetRepository.saveAndFlush(MotifAsset.register(
                artistSeq, "에셋 " + uuid.substring(0, 8), "소개",
                "assets/" + uuid + ".mp4", "assets/" + uuid + "/thumb.jpg", 9_800, AssetType.NORMAL));
    }
}
