package com.ssafy.motif.coreapi.domain.media;

import com.ssafy.motif.event.contract.MediaJobRequestedEvent;
import com.ssafy.motif.event.topology.Exchanges;
import com.ssafy.motif.event.topology.RoutingKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 커밋 후 MQ 발행 (ADR-0005 "DB 먼저, 발행은 커밋 후").
 *
 * <p>트랜잭션이 롤백되면 발행도 없다. 반대로 커밋 전에 발행하면 app-media 가
 * 아직 없는 행을 갱신하려 들어 실패한다 — {@code job.media} 는 우선순위 큐가
 * 아니므로 재시도 간격 안에 행이 생긴다는 보장도 없다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MediaJobEventRelay {

    private final RabbitTemplate rabbitTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void relay(MediaJobRequestedEvent event) {
        rabbitTemplate.convertAndSend(Exchanges.MOTIF_TOPIC, RoutingKeys.JOB_MEDIA, event);
        log.info("job.media 발행: mediaJobUuid={} sourceType={} sourceSeq={} prefix={}",
                event.mediaJobUuid(), event.sourceType(), event.sourceSeq(), event.outputPrefix());
    }
}
