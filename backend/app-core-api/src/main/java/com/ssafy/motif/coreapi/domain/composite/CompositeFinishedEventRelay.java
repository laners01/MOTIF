package com.ssafy.motif.coreapi.domain.composite;

import com.ssafy.motif.event.contract.CompositeFinishedEvent;
import com.ssafy.motif.event.topology.Exchanges;
import com.ssafy.motif.event.topology.RoutingKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 커밋 후 {@code composite.finished} 발행 (작업명세 §3.2·§4.6 — S15P21D104-318).
 *
 * <p><b>커밋 전에 발행하지 않는다.</b> 롤백된 전이로 "합성이 끝났어요" 푸시가 나가면
 * 앱이 §13.3 을 불렀을 때 아직 {@code PROCESSING} 이다 — 사용자가 빈 결과 화면을 본다.
 *
 * <p><b>발행 실패는 로그만 남기고 전이는 유지한다.</b> 알림은 합성의 부수 효과지
 * 조건이 아니다. 여기서 예외를 올리면 {@code AFTER_COMMIT} 이라 롤백도 못 하면서
 * 리스너를 재시도시켜 <b>같은 잡을 다시 전이</b>하게 된다({@code ChallengeEventRelay}
 * 와 같은 판단).
 *
 * <p>{@code x-request-id} 는 {@code RabbitTemplate} post-processor 가 MDC 에서 꺼내
 * 붙인다({@code MqJacksonConfig}) — evt.core 리스너가 복원해 둔 값이라 app-media 에서
 * 넘어온 추적 ID 가 알림까지 이어진다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CompositeFinishedEventRelay {

    private final RabbitTemplate rabbitTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void relay(CompositeFinishedEvent event) {
        try {
            rabbitTemplate.convertAndSend(
                    Exchanges.MOTIF_TOPIC, RoutingKeys.COMPOSITE_FINISHED, event);
            log.info("composite.finished 발행: eventId={} jobUuid={} status={} resultSeq={}",
                    event.eventId(), event.jobUuid(), event.status(), event.resultSeq());
        } catch (RuntimeException e) {
            log.error("composite.finished 발행 실패 — 전이는 유지한다. jobUuid={} status={}",
                    event.jobUuid(), event.status(), e);
        }
    }
}
