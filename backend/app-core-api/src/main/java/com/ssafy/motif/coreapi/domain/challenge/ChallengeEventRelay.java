package com.ssafy.motif.coreapi.domain.challenge;

import com.ssafy.motif.event.contract.ChallengeCreatedEvent;
import com.ssafy.motif.event.topology.Exchanges;
import com.ssafy.motif.event.topology.RoutingKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 커밋 후 {@code challenge.created} 발행 (어드민 명세 §1.6·§4, ADR-0005) — S15P21D104-562.
 *
 * <p><b>커밋 전에 발행하지 않는다.</b> 롤백된 등록으로 구독자 전원에게 푸시가 나가면
 * 되돌릴 방법이 없다 — 앱을 열면 그런 챌린지는 없다.
 *
 * <p><b>발행 실패는 로그만 남기고 등록은 유지한다.</b> 알림은 등록의 부수 효과지 조건이
 * 아니다. 여기서 예외를 올리면 {@code AFTER_COMMIT} 콜백이라 롤백도 못 하면서 이미
 * 커밋된 요청에 500 을 돌려주게 된다 — 어드민은 실패한 줄 알고 같은 챌린지를 또 등록한다.
 * MQ 가 죽어 있던 동안의 알림은 포기한다(재시도 큐는 §8 미결).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChallengeEventRelay {

    private final RabbitTemplate rabbitTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void relay(ChallengeCreatedEvent event) {
        try {
            rabbitTemplate.convertAndSend(Exchanges.MOTIF_TOPIC, RoutingKeys.CHALLENGE_CREATED, event);
            log.info("challenge.created 발행: eventId={} challengeSeq={} artistSeq={}",
                    event.eventId(), event.challengeSeq(), event.artistSeq());
        } catch (RuntimeException e) {
            log.error("challenge.created 발행 실패 — 등록은 유지한다. challengeSeq={}",
                    event.challengeSeq(), e);
        }
    }
}
