package com.ssafy.motif.coreapi.domain.user;

import com.ssafy.motif.event.contract.UserSuspendedEvent;
import com.ssafy.motif.event.topology.Exchanges;
import com.ssafy.motif.event.topology.RoutingKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 커밋 후 {@code user.suspended} 발행 (어드민 명세 §3.14.3·§4) — S15P21D104-582.
 *
 * <p><b>커밋 전에 발행하지 않는다.</b> 롤백된 정지로 기기 토큰이 꺼지면 멀쩡한 회원이
 * 알림만 못 받는 상태로 남는다 — 되돌릴 경로가 없다.
 *
 * <p><b>발행 실패는 로그만 남기고 정지는 유지한다.</b> 정지의 본체는 계정 상태 전이와
 * refresh 토큰 폐기이고 둘 다 이미 커밋됐다. 여기서 예외를 올리면 {@code AFTER_COMMIT}
 * 콜백이라 롤백도 못 하면서 이미 정지된 요청에 500 을 돌려주게 된다 — 운영자는 실패한
 * 줄 알고 같은 회원을 또 정지시키려다 409 를 본다 ({@code ChallengeEventRelay} 와 같은 근거).
 *
 * <p>덧붙여 지금은 <b>소비자도 바인딩도 없다</b> — app-notification 의 리스너는 스텁이고
 * {@code configmap-definitions.yaml} 에 {@code user.suspended} 바인딩이 없다(583).
 * 그래도 발행부를 먼저 두는 이유는, 정지 기능 자체가 이것 없이 성립하기 때문이다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserSuspendedEventRelay {

    private final RabbitTemplate rabbitTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void relay(UserSuspendedEvent event) {
        try {
            rabbitTemplate.convertAndSend(Exchanges.MOTIF_TOPIC, RoutingKeys.USER_SUSPENDED, event);
            log.info("user.suspended 발행: eventId={} userSeq={}", event.eventId(), event.userSeq());
        } catch (RuntimeException e) {
            log.error("user.suspended 발행 실패 — 정지는 유지한다. userSeq={}", event.userSeq(), e);
        }
    }
}
