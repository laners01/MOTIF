package com.ssafy.motif.notification.consumer;

import com.ssafy.motif.event.contract.ChallengeCreatedEvent;
import com.ssafy.motif.event.contract.CompositeJobDoneEvent;
import com.ssafy.motif.event.contract.NoticeCreatedEvent;
import com.ssafy.motif.event.topology.Queues;
import com.ssafy.motif.notification.fanout.SubscriberTokenQuery;
import com.ssafy.motif.notification.push.FcmSender;
import com.ssafy.motif.notification.push.PushMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Supplier;

/**
 * evt.notification 소비 — 구독자 팬아웃 → FCM (S15P21D104-312·313).
 *
 * <p>페이로드 클래스를 리스너 시그니처가 명시한다 — {@code __TypeId__} 헤더에
 * 의존하지 않는다 (ADR-0005). {@code x-request-id} 헤더 → MDC 복원으로
 * API → 큐 → 워커 → 알림 로그가 한 ID로 이어진다.
 *
 * <p><b>발송 전에 멱등 키를 선점하고, 실패하면 반납한다</b>({@link EventIdempotencyGuard}).
 * 선점만 하고 반납하지 않으면 재시도·DLQ 재처리가 "이미 처리됨" 으로 조용히 버려진다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private static final String REQUEST_ID_HEADER = "x-request-id";
    private static final String MDC_KEY = "requestId";

    private final SubscriberTokenQuery subscriberTokenQuery;
    private final FcmSender fcmSender;
    private final EventIdempotencyGuard idempotency;

    /** §3.7.2 — 관심 아티스트 신규 챌린지 (S15P21D104-312). */
    @RabbitListener(queues = Queues.EVT_NOTIFICATION)
    public void onChallengeCreated(ChallengeCreatedEvent event,
                                   @Header(name = REQUEST_ID_HEADER, required = false) String requestId) {
        handle(event.eventId(), requestId, "challenge.created",
                () -> fanout(event.artistSeq(),
                        PushMessage.newChallenge(event.artistName(), event.songTitle(), event.challengeSeq())));
    }

    /** §3.10.2 — 관심 아티스트 신규 공지 (S15P21D104-313). */
    @RabbitListener(queues = Queues.EVT_NOTIFICATION)
    public void onNoticeCreated(NoticeCreatedEvent event,
                                @Header(name = REQUEST_ID_HEADER, required = false) String requestId) {
        handle(event.eventId(), requestId, "notice.created",
                () -> fanout(event.artistSeq(),
                        PushMessage.newNotice(event.artistName(), event.contentPreview(), event.noticeSeq())));
    }

    @RabbitListener(queues = Queues.EVT_NOTIFICATION)
    public void onCompositeDone(CompositeJobDoneEvent event,
                                @Header(name = REQUEST_ID_HEADER, required = false) String requestId) {
        if (requestId != null) {
            MDC.put(MDC_KEY, requestId);
        }
        try {
            log.info("composite.done 수신: jobUuid={} eventId={} occurredAt={}",
                    event.jobUuid(), event.eventId(), event.occurredAt());
            // S15P21D104-319 — 합성 결과 알림. 발행부(§13)가 아직 없다
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    private int fanout(Integer artistSeq, PushMessage message) {
        List<String> tokens = subscriberTokenQuery.activeTokensOfSubscribers(artistSeq);
        return fcmSender.send(message, tokens);
    }

    /**
     * 수신 공통 — MDC 복원 · 멱등 선점 · 실패 시 반납.
     *
     * <p>예외를 삼키지 않는다. 리스너 인터셉터가 3회 재시도하고 소진되면
     * {@code dlq.evt.notification} 으로 격리한다({@code MqConfig}).
     */
    private void handle(String eventId, String requestId, String kind, Supplier<Integer> action) {
        if (requestId != null) {
            MDC.put(MDC_KEY, requestId);
        }
        try {
            if (!idempotency.claim(eventId)) {
                log.info("{} 재전달 — 건너뛴다: eventId={}", kind, eventId);
                return;
            }
            try {
                int sent = action.get();
                log.info("{} 발송 완료: eventId={} sent={}", kind, eventId, sent);
            } catch (RuntimeException e) {
                idempotency.release(eventId);
                throw e;
            }
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
