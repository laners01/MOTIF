package com.ssafy.motif.coreapi.domain.media;

import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.coreapi.global.media.CloudFrontUrlSigner;
import com.ssafy.motif.coreapi.global.media.MediaPlaylistService;
import com.ssafy.motif.domain.core.job.CompositeJob;
import com.ssafy.motif.domain.core.job.CompositeJobRepository;
import com.ssafy.motif.domain.core.job.JobStatus;
import com.ssafy.motif.domain.core.job.JobType;
import com.ssafy.motif.domain.core.video.ParticipationVideo;
import com.ssafy.motif.domain.core.video.ParticipationVideoRepository;
import com.ssafy.motif.domain.core.video.Visibility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 보호 등급 재생목록의 <b>인가</b> (S15P21D104-278 · 명세 §1.7).
 *
 * <p>여기서 고정하는 것은 "누가 볼 수 있는가" 하나다. 재생목록을 다시 쓰는 일은
 * {@link MediaPlaylistService} 의 몫이라 목으로 세우고, 그 호출이 <b>일어났는지 아닌지</b>로
 * 통과·거부를 읽는다 — 거부인데 S3 를 읽으러 갔다면 그것만으로 틀린 것이다.
 *
 * <p><b>이 분기는 틀려도 증상이 조용하다.</b> 남의 비공개 영상을 내주는 쪽으로 틀리면
 * 아무 에러도 나지 않고 재생만 잘 된다. 그래서 테스트로 묶는다.
 */
class MediaPlaylistQueryServiceTest {

    private static final int OWNER = 7;
    private static final int STRANGER = 99;
    private static final String UUID_TEXT = "9c1d2e3f-4a5b-6c7d-8e9f-0a1b2c3d4e5f";
    private static final String PREFIX = "private/motif/" + UUID_TEXT;
    private static final String MASTER_KEY = PREFIX + "/master.m3u8";
    private static final String BODY = "#EXTM3U";

    private CompositeJobRepository jobRepository;
    private ParticipationVideoRepository videoRepository;
    private MediaPlaylistService playlistService;
    private MediaPlaylistQueryService service;

    @BeforeEach
    void setUp() {
        jobRepository = mock(CompositeJobRepository.class);
        videoRepository = mock(ParticipationVideoRepository.class);
        playlistService = mock(MediaPlaylistService.class);
        service = new MediaPlaylistQueryService(jobRepository, videoRepository, playlistService,
                mock(CloudFrontUrlSigner.class));

        given(playlistService.master(anyString(), anyString())).willReturn(BODY);
        given(playlistService.variant(anyString(), anyString())).willReturn(BODY);
    }

    @Nested
    @DisplayName("모티프 결과물")
    class Motif {

        @Test
        @DisplayName("소유자는 받는다")
        void ownerGetsPlaylist() {
            givenJob(motifJob(JobStatus.DONE));

            assertThat(service.master(OWNER, UUID_TEXT)).isEqualTo(BODY);
            verify(playlistService).master(PREFIX, "/api/v1/media/" + UUID_TEXT);
        }

        @Test
        @DisplayName("타인은 404 — 모티프 결과물에는 공개 등급이 없다")
        void strangerIsRejected() {
            givenJob(motifJob(JobStatus.DONE));

            assertRejected(() -> service.master(STRANGER, UUID_TEXT));
        }
    }

    @Nested
    @DisplayName("챌린지 참여 영상")
    class Challenge {

        @Test
        @DisplayName("게시 전(행 없음)은 소유자만 — 프리뷰도 재생 대상이다")
        void previewIsOwnerOnly() {
            givenJob(challengeJob(JobStatus.DONE));
            given(videoRepository.findByJobSeq(any())).willReturn(Optional.empty());

            assertThat(service.master(OWNER, UUID_TEXT)).isEqualTo(BODY);
            assertRejected(() -> service.master(STRANGER, UUID_TEXT));
        }

        @Test
        @DisplayName("PUBLIC 게시물은 누구나 본다")
        void publishedPublicIsOpen() {
            givenJob(challengeJob(JobStatus.DONE));
            givenVideo(video(Visibility.PUBLIC, false));

            assertThat(service.master(STRANGER, UUID_TEXT)).isEqualTo(BODY);
        }

        @Test
        @DisplayName("PRIVATE 게시물은 소유자만")
        void publishedPrivateIsOwnerOnly() {
            givenJob(challengeJob(JobStatus.DONE));
            givenVideo(video(Visibility.PRIVATE, false));

            assertThat(service.master(OWNER, UUID_TEXT)).isEqualTo(BODY);
            assertRejected(() -> service.master(STRANGER, UUID_TEXT));
        }

        @Test
        @DisplayName("삭제된 게시물은 소유자에게도 안 내준다 (§1.8)")
        void deletedIsClosedEvenForOwner() {
            givenJob(challengeJob(JobStatus.DONE));
            givenVideo(video(Visibility.PUBLIC, true));

            assertRejected(() -> service.master(OWNER, UUID_TEXT));
        }
    }

    @Nested
    @DisplayName("재생할 수 없는 상태")
    class NotPlayable {

        @Test
        @DisplayName("잡이 없으면 404")
        void unknownJob() {
            given(jobRepository.findByJobUuid(any())).willReturn(Optional.empty());

            assertRejected(() -> service.master(OWNER, UUID_TEXT));
        }

        @Test
        @DisplayName("UUID 형식이 아니면 404 — 잡 조회까지 가지 않는다")
        void malformedUuid() {
            assertRejected(() -> service.master(OWNER, "not-a-uuid"));
            verify(jobRepository, never()).findByJobUuid(any());
        }

        @Test
        @DisplayName("가공이 끝나지 않았으면 404 — 산출물 키가 NULL 이다")
        void jobStillProcessing() {
            CompositeJob job = motifJob(JobStatus.PROCESSING);
            ReflectionTestUtils.setField(job, "resultVideoKey", null);
            givenJob(job);

            assertRejected(() -> service.master(OWNER, UUID_TEXT));
        }

        @Test
        @DisplayName("사다리에 없는 화질은 404 — S3 키에 이어붙기 전에 끊는다")
        void unknownRendition() {
            givenJob(motifJob(JobStatus.DONE));

            assertRejected(() -> service.variant(OWNER, UUID_TEXT, "../../private/motif/남의것"));
            verify(playlistService, never()).variant(anyString(), anyString());
        }

        @Test
        @DisplayName("사다리에 있어도 S3 에 없으면 404 — 원본보다 큰 단은 만들어지지 않는다")
        void renditionMissingInStorage() {
            givenJob(motifJob(JobStatus.DONE));
            given(playlistService.variant(PREFIX, "1080p"))
                    .willThrow(new MediaPlaylistService.MediaPlaylistNotFoundException(
                            PREFIX + "/1080p/index.m3u8", new RuntimeException()));

            assertRejected(() -> service.variant(OWNER, UUID_TEXT, "1080p"));
        }
    }

    @Test
    @DisplayName("화질별 재생목록도 마스터와 같은 인가를 받는다")
    void variantSharesAuthorization() {
        givenJob(motifJob(JobStatus.DONE));

        assertThat(service.variant(OWNER, UUID_TEXT, "720p")).isEqualTo(BODY);
        assertRejected(() -> service.variant(STRANGER, UUID_TEXT, "720p"));
    }

    // ── 도우미 ──────────────────────────────────────────────────────────

    private void givenJob(CompositeJob job) {
        given(jobRepository.findByJobUuid(UUID.fromString(UUID_TEXT))).willReturn(Optional.of(job));
    }

    private void givenVideo(ParticipationVideo video) {
        given(videoRepository.findByJobSeq(1L)).willReturn(Optional.of(video));
    }

    private static void assertRejected(Runnable call) {
        assertThatThrownBy(call::run)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(MediaErrorCode.PLAYLIST_NOT_FOUND);
    }

    private static CompositeJob motifJob(JobStatus status) {
        return job(JobType.MOTIF, status);
    }

    private static CompositeJob challengeJob(JobStatus status) {
        return job(JobType.CHALLENGE, status);
    }

    private static CompositeJob job(JobType type, JobStatus status) {
        CompositeJob job = newInstance(CompositeJob.class);
        ReflectionTestUtils.setField(job, "jobSeq", 1L);
        ReflectionTestUtils.setField(job, "jobUuid", UUID.fromString(UUID_TEXT));
        ReflectionTestUtils.setField(job, "jobType", type);
        ReflectionTestUtils.setField(job, "userSeq", OWNER);
        ReflectionTestUtils.setField(job, "status", status);
        ReflectionTestUtils.setField(job, "resultVideoKey", MASTER_KEY);
        return job;
    }

    private static ParticipationVideo video(Visibility visibility, boolean deleted) {
        ParticipationVideo video = newInstance(ParticipationVideo.class);
        ReflectionTestUtils.setField(video, "jobSeq", 1L);
        ReflectionTestUtils.setField(video, "userSeq", OWNER);
        ReflectionTestUtils.setField(video, "visibility", visibility);
        ReflectionTestUtils.setField(video, "deleted", deleted);
        return video;
    }

    /** 엔티티는 생성 경로가 없다(§8.1·§13 이 만든다) — 읽기용 인스턴스는 리플렉션으로 세운다. */
    private static <T> T newInstance(Class<T> type) {
        try {
            Constructor<T> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(type.getSimpleName() + " 인스턴스를 만들지 못했다", e);
        }
    }
}
