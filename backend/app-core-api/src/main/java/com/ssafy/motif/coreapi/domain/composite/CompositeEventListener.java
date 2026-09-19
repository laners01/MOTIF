package com.ssafy.motif.coreapi.domain.composite;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.motif.coreapi.domain.composite.service.CompositeJobCompletionService;
import com.ssafy.motif.coreapi.global.filter.RequestIdFilter;
import com.ssafy.motif.event.contract.CompositePackagedEvent;
import com.ssafy.motif.event.contract.MediaJobRequestedEvent;
import com.ssafy.motif.event.topology.Queues;
import com.ssafy.motif.event.topology.RoutingKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * {@code evt.core} 소비 (작업명세 §4.4 — S15P21D104-189).
 *
 * <p><b>리스너는 하나다. 타입은 라우팅 키로 가른다</b>({@code NotificationEventListener}
 * 와 같은 이유). 한 큐에 {@code @RabbitListener} 를 여러 개 달면 타입별로 갈리는 것이
 * 아니라 컨슈머가 그 수만큼 생겨 브로커가 라운드로빈으로 나눠준다 — 게다가 MQ 컨버터가
 * {@code FAIL_ON_UNKNOWN_PROPERTIES} 를 꺼둬서 엉뚱한 타입으로 <b>변환이 성공해버린다</b>.
 *
 * <p><b>{@code __TypeId__} 에 의존하지 않는다</b>(ADR-0005). {@code Message} 를 그대로
 * 받아 우리가 고른 타입으로 푼다 — 클래스를 옮겨도 소비가 깨지지 않고, 자바가 아닌
 * 발행자도 받을 수 있다.
 *
 * <p>{@code x-request-id} → MDC 복원. 여기서 복원해 둔 값이 이 트랜잭션의
 * {@code composite.finished} 발행에 그대로 실린다 — API → 큐 → app-media → core →
 * 알림이 한 ID 로 이어진다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CompositeEventListener {

    private static final String REQUEST_ID_HEADER = "x-request-id";

    private final CompositeJobCompletionService completionService;
    /** MQ 직렬화 전용 매퍼(snake_case) — {@code MqJacksonConfig} 가 만든 것과 같아야 한다. */
    private final ObjectMapper mqObjectMapper;

    @RabbitListener(queues = Queues.EVT_CORE)
    public void onEvent(Message message,
                        @Header(AmqpHeaders.RECEIVED_ROUTING_KEY) String routingKey,
                        @Header(name = REQUEST_ID_HEADER, required = false) String requestId) {
        if (requestId != null) {
            MDC.put(RequestIdFilter.MDC_KEY, requestId);
        }
        try {
            switch (routingKey) {
                case RoutingKeys.COMPOSITE_PACKAGED -> {
                    CompositePackagedEvent event = read(message, CompositePackagedEvent.class);
                    // 챌린지 원본·에셋 미리보기는 app-media 가 직접 갱신한다 — 여기 올 일이
                    // 없지만, 같은 라우팅 키를 쓰게 되면 조용히 잡을 건드리게 된다
                    if (!MediaJobRequestedEvent.SOURCE_COMPOSITE_RESULT.equals(event.sourceType())) {
                        log.debug("composite.packaged 무시 — sourceType={}", event.sourceType());
                        return;
                    }
                    completionService.packaged(event);
                }
                // core 자신이 낸 것이다. 알림이 처리하고 우리는 버린다 —
                // composite.* 바인딩이 두 큐에 함께 걸려 있어 여기로도 온다
                case RoutingKeys.COMPOSITE_FINISHED -> log.debug("composite.finished 자가 수신 — ack");
                // 던져서 DLQ 로 보낸다. 조용히 버리면 바인딩이 늘었을 때 그 이벤트가
                // 통째로 사라지는데 아무 흔적이 남지 않는다
                default -> throw new IllegalStateException("처리할 수 없는 라우팅 키: " + routingKey);
            }
        } finally {
            MDC.remove(RequestIdFilter.MDC_KEY);
        }
    }

    /**
     * 페이로드를 라우팅 키가 지정한 타입으로 읽는다.
     *
     * <p>메서드 파라미터가 {@code Message} 라 컨버터가 개입하지 않는다 — 바이트를
     * 직접 받아 우리가 고른 타입으로 푼다.
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
