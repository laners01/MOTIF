package com.ssafy.motif.domain.core.job;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CompositeJobRepository extends JpaRepository<CompositeJob, Long> {

    /**
     * 보호 등급 재생목록 인가의 출발점 (API 명세 §1.7).
     *
     * <p>주소에 실려 오는 것은 {@code jobUuid} 뿐이라, 타입·소유자·산출물 키를 여기서
     * 한 번에 집는다. {@code uq_composite_jobs_job_uuid} 가 유일성을 보장한다.
     */
    Optional<CompositeJob> findByJobUuid(UUID jobUuid);

    /**
     * 접수되지 못한 채 멈춘 잡 (작업명세 §4.7 — 디스패처 1번).
     *
     * <p><b>유료가 먼저다.</b> 큐가 없어지면서 {@code x-max-priority} 로 주던 모티프
     * 우선(5 vs 0)이 사라졌다 — 그 자리를 이 {@code order by} 가 메운다. GPU 쿼터가
     * 빠듯할 때 무료 챌린지가 유료 모티프를 밀어내지 않는다.
     *
     * <p>{@code COALESCE(mod_dttm, reg_dttm)} 은 {@code ix_composite_jobs_stuck} 의
     * 표현식과 같은 모양이다 — 다르게 쓰면 부분 인덱스를 타지 않는다.
     *
     * @param before {@code now - requeue-after}. 방금 §13.2 가 만든 잡을 즉시 집지 않는다
     */
    @Query("""
            select j from CompositeJob j
             where j.status = com.ssafy.motif.domain.core.job.JobStatus.QUEUED
               and coalesce(j.modDttm, j.regDttm) < :before
             order by case when j.jobType = com.ssafy.motif.domain.core.job.JobType.MOTIF
                           then 0 else 1 end, j.regDttm
            """)
    List<CompositeJob> findStuckQueued(@Param("before") Instant before, Pageable pageable);

    /**
     * 접수는 됐는데 콜백이 오지 않은 잡 (작업명세 §4.7 — 디스패처 2번).
     *
     * <p>AI Job 이 task-timeout·OOM 으로 죽었거나, 콜백 HTTP 가 유실됐거나,
     * app-media 가공이 {@code dlq.job.media} 로 갔거나 — 셋 다 여기로 모인다.
     * <b>결과가 S3 에 있으면 콜백 유실</b>이므로 살릴 수 있다.
     *
     * <p>우선순위를 두지 않는다. 이쪽은 이미 GPU 를 쓴 뒤라 순서가 비용에 영향이 없다.
     *
     * @param before {@code now - (task-timeout + 5분)}
     */
    @Query("""
            select j from CompositeJob j
             where j.status = com.ssafy.motif.domain.core.job.JobStatus.PROCESSING
               and coalesce(j.modDttm, j.regDttm) < :before
             order by j.regDttm
            """)
    List<CompositeJob> findStuckProcessing(@Param("before") Instant before, Pageable pageable);
}
