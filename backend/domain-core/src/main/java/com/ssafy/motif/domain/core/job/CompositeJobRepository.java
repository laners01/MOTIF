package com.ssafy.motif.domain.core.job;

import org.springframework.data.jpa.repository.JpaRepository;

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
}
