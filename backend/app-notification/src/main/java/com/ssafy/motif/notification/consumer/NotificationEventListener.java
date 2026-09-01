package com.ssafy.motif.notification.consumer;

import com.ssafy.motif.event.contract.CompositeJobDoneEvent;
import com.ssafy.motif.event.topology.Queues;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * evt.notification 소비 (Phase 0: 수신 확인만).
 *
 * <p>페이로드 클래스를 리스너 시그니처가 명시한다 — {@code __TypeId__} 헤더에
 * 의존하지 않는다 (ADR-0005). {@code x-request-id} 헤더 → MDC 복원으로
 * API → 큐 → 워커 → 알림 로그가 한 ID로 이어진다.
 */
@Slf4j
@Component
public class NotificationEventListener {

    private static final String REQUEST_ID_HEADER = "x-request-id";
    private static final String MDC_KEY = "requestId";

    @RabbitListener(queues = Queues.EVT_NOTIFICATION)
    public void onCompositeDone(CompositeJobDoneEvent event,
                                @Header(name = REQUEST_ID_HEADER, required = false) String requestId) {
        if (requestId != null) {
            MDC.put(MDC_KEY, requestId);
        }
        try {
            log.info("composite.done 수신: jobUuid={} eventId={} occurredAt={}",
                    event.jobUuid(), event.eventId(), event.occurredAt());
            // Phase 1: 멱등 가드(job_uuid + 상태 전이) → 알림 저장 → 팬아웃·FCM
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
