package com.ssafy.motif.coreapi.domain.comment;

import com.ssafy.motif.event.contract.CommentCreatedEvent;
import com.ssafy.motif.event.topology.Exchanges;
import com.ssafy.motif.event.topology.RoutingKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 커밋 후 {@code comment.created} · {@code comment.replied} 발행 (API §9.3 · ADR-0005).
 *
 * <p>{@link com.ssafy.motif.coreapi.domain.notice.NoticeEventRelay} 와 같은 모양이다 —
 * 커밋 전에 보내면 롤백된 댓글로 알림이 나가고, 앱을 열면 그런 댓글이 없다.
 *
 * <p><b>발행 실패는 로그만 남기고 댓글은 유지한다.</b> 알림은 댓글의 부수 효과지 조건이
 * 아니다. {@code AFTER_COMMIT} 이라 여기서 예외를 올려도 롤백은 못 하고 이미 커밋된
 * 요청에 500 만 돌려준다 — 사용자는 실패한 줄 알고 같은 댓글을 다시 단다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CommentEventRelay {

    private final RabbitTemplate rabbitTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void relay(CommentCreatedEvent event) {
        String routingKey = event.isReply() ? RoutingKeys.REPLY_CREATED : RoutingKeys.COMMENT_CREATED;
        try {
            rabbitTemplate.convertAndSend(Exchanges.MOTIF_TOPIC, routingKey, event);
            log.info("{} 발행: eventId={} commentSeq={} recipient={}",
                    routingKey, event.eventId(), event.commentSeq(), event.recipientUserSeq());
        } catch (RuntimeException e) {
            log.error("{} 발행 실패 — 댓글은 유지한다. commentSeq={}",
                    routingKey, event.commentSeq(), e);
        }
    }
}
