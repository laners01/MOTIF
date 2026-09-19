package com.ssafy.motif.coreapi.domain.composite.service;

import com.ssafy.motif.domain.core.job.CompositeJob;
import com.ssafy.motif.domain.core.job.JobStatus;
import com.ssafy.motif.domain.core.job.JobType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@code composite_jobs} 상태 전이 가드 (작업명세 §2 — S15P21D104-189).
 *
 * <p><b>전이가 어긋나면 DB CHECK 에 걸리기 전에 여기서 잡는다.</b>
 * {@code ck_composite_jobs_status_shape} 에 걸리면 리스너가 재시도 3회를 돌고
 * {@code dlq.*} 로 가는데, 로그에는 제약 이름만 남아 어느 전이가 틀렸는지 보이지
 * 않는다. 예전 app-media 가 정확히 그 상태로 죽어 있었다(§7 함정 4).
 *
 * <p>순수 상태 기계라 DB 도 스프링도 없다.
 */
class CompositeJobTransitionTest {

    private static final String MASTER = "private/challenge/x/master.m3u8";
    private static final String THUMB = "private/challenge/x/thumb.jpg";

    @Test
    @DisplayName("정상 경로 — UPLOADED → QUEUED → PROCESSING → DONE, 키는 DONE 에서만 찬다")
    void happyPath() {
        CompositeJob job = uploaded();
        assertThat(job.getStatus()).isEqualTo(JobStatus.UPLOADED);
        assertThat(job.getResultVideoKey()).isNull();

        job.queue(true);
        assertThat(job.getStatus()).isEqualTo(JobStatus.QUEUED);
        assertThat(job.isCorrectionEnabled()).isTrue();

        job.markSubmitted();
        assertThat(job.getStatus()).isEqualTo(JobStatus.PROCESSING);

        job.complete(MASTER, THUMB);
        assertThat(job.getStatus()).isEqualTo(JobStatus.DONE);
        assertThat(job.getResultVideoKey()).isEqualTo(MASTER);
        assertThat(job.getResultThumbnailKey()).isEqualTo(THUMB);
        assertThat(job.getFailCode()).isNull();
    }

    @Test
    @DisplayName("fail 은 산출물 키를 지운다 — FAILED 행에 키가 남으면 CHECK 위반이다")
    void failClearsResultKeys() {
        // 정상 경로에서는 호출부가 DONE 을 먼저 거르지만(완료가 실패보다 우선한다),
        // 키를 지우지 않는 fail 은 그 가드가 뚫린 순간 CHECK 로 터진다
        CompositeJob job = processing();
        job.complete(MASTER, THUMB);

        job.fail("FACE_NOT_DETECTED");

        assertThat(job.getStatus()).isEqualTo(JobStatus.FAILED);
        assertThat(job.getFailCode()).isEqualTo("FACE_NOT_DETECTED");
        assertThat(job.getResultVideoKey()).isNull();
        assertThat(job.getResultThumbnailKey()).isNull();
    }

    @Test
    @DisplayName("markSubmitted 는 fail_code 를 지운다 — PROCESSING 행에 남으면 CHECK 위반이다")
    void resubmitClearsFailCode() {
        CompositeJob job = processing();
        job.fail("STUCK");

        job.markSubmitted();

        assertThat(job.getStatus()).isEqualTo(JobStatus.PROCESSING);
        assertThat(job.getFailCode()).isNull();
    }

    @Test
    @DisplayName("retry 는 attempt 를 올리고 사유를 지운다. jobUuid 는 그대로다")
    void retryBumpsAttempt() {
        CompositeJob job = processing();
        UUID uuid = job.getJobUuid();
        job.fail("FACE_NOT_DETECTED");

        job.retry();

        assertThat(job.getStatus()).isEqualTo(JobStatus.QUEUED);
        assertThat(job.getAttempt()).isEqualTo((short) 2);
        assertThat(job.getFailCode()).isNull();
        assertThat(job.getJobUuid()).isEqualTo(uuid);
    }

    @Test
    @DisplayName("어긋난 전이는 던진다 — CHECK 가 아니라 여기서 멈춘다")
    void wrongTransitionsThrow() {
        assertThatThrownBy(() -> processing().queue(true))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> uploaded().markSubmitted())
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> processing().retry())
                .isInstanceOf(IllegalStateException.class);

        CompositeJob done = processing();
        done.complete(MASTER, THUMB);
        assertThatThrownBy(() -> done.complete(MASTER, THUMB))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("키 없는 complete·사유 없는 fail 은 인자 오류다 — null 을 DB 까지 보내지 않는다")
    void nullArgumentsRejected() {
        assertThatThrownBy(() -> processing().complete(MASTER, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> processing().fail(" "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static CompositeJob uploaded() {
        return CompositeJob.create(UUID.randomUUID(), JobType.CHALLENGE, 7, 11, null, null,
                "raw/x.mp4");
    }

    private static CompositeJob processing() {
        CompositeJob job = uploaded();
        job.queue(true);
        job.markSubmitted();
        return job;
    }
}
