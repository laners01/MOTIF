package com.ssafy.motif.media.global.config;

import com.ssafy.motif.event.contract.MediaJobRequestedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MQ 컨버터 왕복 — 신뢰 패키지 회귀 방어 (S15P21D104-312).
 *
 * <p>알림 모듈이 2026-09-17 에 이 결함으로 멈췄고({@code dlq.evt.notification} 5건),
 * 이 모듈도 같은 구성이라 <b>같은 자리에서 터질 예정이었다</b> — {@code job.media} 에
 * 아직 메시지가 흐르지 않아 드러나지 않았을 뿐이다. 리스너가 이벤트를 타입 파라미터로
 * 받으므로 변환에 실패하면 본문은 한 줄도 돌지 않는다.
 */
class MqConverterTest {

    private final Jackson2JsonMessageConverter converter =
            new MqConfig().mqMessageConverter(new MqConfig().mqObjectMapper());

    @Test
    @DisplayName("job.media 요청이 왕복한다 — 계약 패키지가 신뢰 목록에 있다")
    void mediaJobRequestedRoundTrips() {
        MediaJobRequestedEvent event = new MediaJobRequestedEvent(
                "evt-1", Instant.parse("2026-09-17T13:57:05Z"), "job-uuid",
                MediaJobRequestedEvent.SOURCE_CHALLENGE_ORIGIN, 12L,
                "challenges/abc.mp4", "private/challenge/abc/", 1500L);

        Message message = converter.toMessage(event, new MessageProperties());
        String typeId = message.getMessageProperties().getHeader("__TypeId__");
        assertThat(typeId).isEqualTo(MediaJobRequestedEvent.class.getName());
        assertThat(converter.fromMessage(message)).isEqualTo(event);
    }
}
