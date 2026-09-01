package com.ssafy.motif.coreapi.dev;

import com.ssafy.motif.event.contract.CompositeJobRequestedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * AFTER_COMMIT 발행 규약의 실물 (ADR-0005 "DB 먼저, 발행은 커밋 후") — 스모크 겸용.
 *
 * <p>서비스는 트랜잭션 <b>안에서</b> RabbitTemplate을 부르지 않는다. 내부 이벤트만
 * 올리고, MQ 발행은 {@link DevJobEventRelay}가 커밋 후에 수행한다.
 * Phase 1에서 composite_jobs 행 저장(아웃박스 염가판)이 이 트랜잭션에 들어온다.
 */
@Service
@RequiredArgsConstructor
public class DevJobService {

    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public String requestCompositeJob(String jobType) {
        String jobUuid = UUID.randomUUID().toString();
        eventPublisher.publishEvent(new CompositeJobRequestedEvent(
                UUID.randomUUID().toString(), Instant.now(), jobUuid, jobType));
        return jobUuid;
    }
}
