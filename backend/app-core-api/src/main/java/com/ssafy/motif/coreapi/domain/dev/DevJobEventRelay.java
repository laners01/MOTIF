package com.ssafy.motif.coreapi.domain.dev;

import com.ssafy.motif.event.contract.CompositeJobRequestedEvent;
import com.ssafy.motif.event.topology.Exchanges;
import com.ssafy.motif.event.topology.RoutingKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 커밋 후 MQ 발행기 (ADR-0005). 트랜잭션이 롤백되면 발행도 없다.
 * job.composite는 x-max-priority=10 — 모티프(유료) 5, 챌린지(무료) 0으로
 * 돈을 낸 사용자를 먼저 처리한다 (아키텍처 설계서 §5.4).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DevJobEventRelay {

    private static final int PRIORITY_MOTIF = 5;
    private static final int PRIORITY_CHALLENGE = 0;

    private final RabbitTemplate rabbitTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void relay(CompositeJobRequestedEvent event) {
        int priority = "MOTIF".equals(event.jobType()) ? PRIORITY_MOTIF : PRIORITY_CHALLENGE;
        rabbitTemplate.convertAndSend(Exchanges.MOTIF_TOPIC, RoutingKeys.JOB_COMPOSITE, event,
                message -> {
                    message.getMessageProperties().setPriority(priority);
                    return message;
                });
        log.info("job.composite 발행: jobUuid={} jobType={} priority={}",
                event.jobUuid(), event.jobType(), priority);
    }
}
