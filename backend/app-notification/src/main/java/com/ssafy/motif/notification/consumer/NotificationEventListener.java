package com.ssafy.motif.notification.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.motif.event.contract.ChallengeCreatedEvent;
import com.ssafy.motif.event.contract.CompositeJobDoneEvent;
import com.ssafy.motif.event.contract.NoticeCreatedEvent;
import com.ssafy.motif.event.topology.Queues;
import com.ssafy.motif.event.topology.RoutingKeys;
import com.ssafy.motif.notification.fanout.SubscriberTokenQuery;
import com.ssafy.motif.notification.push.FcmSender;
import com.ssafy.motif.notification.push.PushMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.function.Supplier;

/**
 * evt.notification 소비 — 구독자 팬아웃 → FCM (S15P21D104-312·313).
 *
 * <p><b>리스너는 하나다. 타입은 라우팅 키로 가른다.</b> 한 큐에 {@code @RabbitListener}
 * 를 여러 개 달면 <b>타입별로 갈리는 것이 아니라</b> 컨슈머가 그 수만큼 생겨 브로커가
 * 메시지를 라운드로빈으로 나눠준다 — {@code challenge.created} 가 공지 리스너로 가는
 * 일이 생긴다. 게다가 MQ 컨버터가 {@code FAIL_ON_UNKNOWN_PROPERTIES} 를 꺼둬서
 * <b>변환이 성공해버린다</b>: 챌린지 이벤트가 {@code noticeSeq=null}·
 * {@code contentPreview=null} 인 공지 이벤트로 둔갑해 "…공지 / 본문 없음" 푸시가
 * 정상처럼 나간다. 예외도 DLQ 도 남지 않는다.
 *
 * <p><b>{@code @RabbitHandler} 로 가르지 않는 이유.</b> 그 방식은 발신자가 붙인
 * {@code __TypeId__}(자바 클래스명) 헤더를 본다. ADR-0005 가 그것을 금지한 이유가
 * 둘이다 — 클래스를 옮기거나 이름을 바꾸면 소비가 깨지고, <b>자바가 아닌 발행자</b>
 * (AI 서버의 {@code composite.done})는 그 헤더를 붙이지 않는다. 라우팅 키는 우리가
 * 정한 계약이라 두 문제가 다 없다.
 *
 * <p>{@code x-request-id} 헤더 → MDC 복원으로 API → 큐 → 워커 → 알림 로그가 한 ID 로
 * 이어진다.
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
    /** MQ 직렬화 전용 매퍼(snake_case) — {@code MqConfig} 가 만든 것과 같아야 한다. */
    private final ObjectMapper mqObjectMapper;

    @RabbitListener(queues = Queues.EVT_NOTIFICATION)
    public void onEvent(Message message,
                        @Header(AmqpHeaders.RECEIVED_ROUTING_KEY) String routingKey,
                        @Header(name = REQUEST_ID_HEADER, required = false) String requestId) {
        if (requestId != null) {
            MDC.put(MDC_KEY, requestId);
        }
        try {
            switch (routingKey) {
                // §3.7.2 — 관심 아티스트 신규 챌린지 (S15P21D104-312)
                case RoutingKeys.CHALLENGE_CREATED -> {
                    ChallengeCreatedEvent event = read(message, ChallengeCreatedEvent.class);
                    handle(event.eventId(), routingKey, () -> fanout(event.artistSeq(),
                            PushMessage.newChallenge(event.artistName(), event.songTitle(),
                                    event.challengeSeq())));
                }
                // §3.10.2 — 관심 아티스트 신규 공지 (S15P21D104-313)
                case RoutingKeys.NOTICE_CREATED -> {
                    NoticeCreatedEvent event = read(message, NoticeCreatedEvent.class);
                    handle(event.eventId(), routingKey, () -> fanout(event.artistSeq(),
                            PushMessage.newNotice(event.artistName(), event.contentPreview(),
                                    event.noticeSeq())));
                }
                case RoutingKeys.COMPOSITE_DONE -> {
                    CompositeJobDoneEvent event = read(message, CompositeJobDoneEvent.class);
                    log.info("composite.done 수신: jobUuid={} eventId={}",
                            event.jobUuid(), event.eventId());
                    // S15P21D104-319 — 합성 결과 알림. 발행부(§13)가 아직 없다
                }
                // 던져서 DLQ 로 보낸다. 조용히 버리면 바인딩이 늘었을 때 그 알림이
                // 통째로 사라지는데 아무 흔적이 남지 않는다
                default -> throw new IllegalStateException(
                        "처리할 수 없는 라우팅 키: " + routingKey);
            }
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    private int fanout(Integer artistSeq, PushMessage message) {
        List<String> tokens = subscriberTokenQuery.activeTokensOfSubscribers(artistSeq);
        return fcmSender.send(message, tokens);
    }

    /**
     * 멱등 선점 · 실패 시 반납.
     *
     * <p>예외를 삼키지 않는다. 리스너 인터셉터가 3회 재시도하고 소진되면
     * {@code dlq.evt.notification} 으로 격리한다({@code MqConfig}).
     */
    private void handle(String eventId, String routingKey, Supplier<Integer> action) {
        if (!idempotency.claim(eventId)) {
            log.info("{} 재전달 — 건너뛴다: eventId={}", routingKey, eventId);
            return;
        }
        try {
            int sent = action.get();
            log.info("{} 발송 완료: eventId={} sent={}", routingKey, eventId, sent);
        } catch (RuntimeException e) {
            idempotency.release(eventId);
            throw e;
        }
    }

    /**
     * 페이로드를 라우팅 키가 지정한 타입으로 읽는다.
     *
     * <p>메서드 파라미터가 {@code Message} 라 컨버터가 개입하지 않는다 — 바이트를
     * 직접 받아 우리가 고른 타입으로 푼다. 그래서 {@code __TypeId__} 가 없어도 되고,
     * 있어도 무시한다.
     */
    private <T> T read(Message message, Class<T> type) {
        try {
            return mqObjectMapper.readValue(message.getBody(), type);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "페이로드를 " + type.getSimpleName() + " 로 읽지 못했다", e);
        }
    }
}
