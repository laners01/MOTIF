package com.ssafy.motif.notification.global.config;

import com.ssafy.motif.event.contract.ChallengeCreatedEvent;
import com.ssafy.motif.event.contract.NoticeCreatedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MQ 컨버터 왕복 — 신뢰 패키지 회귀 방어 (S15P21D104-312·313).
 *
 * <p><b>2026-09-17 운영 장애.</b> 발행측이 {@code __TypeId__} 에 FQCN 을 싣는데 수신측
 * 신뢰 패키지가 기본값({@code java.util}·{@code java.lang}) 이라 계약 클래스가 전부
 * 거부됐다. 공지·챌린지 알림이 한 건도 나가지 못하고 {@code dlq.evt.notification} 에
 * 5건이 쌓였다.
 *
 * <p><b>기존 테스트로는 잡히지 않았다.</b> 리스너 테스트는 이벤트 객체를 직접 넘겨
 * 컨버터를 건너뛴다 — 실패 지점이 리스너 <b>이전</b>인 메시지 변환 단계였다.
 * 그래서 브로커 없이 컨버터만 왕복시키는 이 검사가 따로 있어야 한다.
 */
class MqConverterTest {

    private final Jackson2JsonMessageConverter converter =
            new MqConfig().mqMessageConverter(new MqConfig().mqObjectMapper());

    @Test
    @DisplayName("challenge.created 가 왕복한다 — 계약 패키지가 신뢰 목록에 있다")
    void challengeCreatedRoundTrips() {
        ChallengeCreatedEvent event = new ChallengeCreatedEvent(
                "evt-1", Instant.parse("2026-09-17T13:57:05Z"), 12, 1, "아이유", "밤편지",
                "challenges/abc/thumb.jpg");

        assertThat(roundTrip(event)).isEqualTo(event);
    }

    @Test
    @DisplayName("notice.created 가 왕복한다")
    void noticeCreatedRoundTrips() {
        NoticeCreatedEvent event = new NoticeCreatedEvent(
                "evt-2", Instant.parse("2026-09-17T13:57:05Z"), 3, 1, "아이유", "공지 미리보기");

        assertThat(roundTrip(event)).isEqualTo(event);
    }

    /** 발행측과 같은 컨버터로 싣고 수신측처럼 되읽는다 — {@code __TypeId__} 가 여기서 붙는다. */
    private Object roundTrip(Object event) {
        Message message = converter.toMessage(event, new MessageProperties());
        String typeId = message.getMessageProperties().getHeader("__TypeId__");
        assertThat(typeId).isEqualTo(event.getClass().getName());
        return converter.fromMessage(message);
    }
}
