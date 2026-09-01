package com.ssafy.motif.coreapi.global.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ssafy.motif.coreapi.global.filter.RequestIdFilter;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MQ 경계 전용 Jackson 2 구성 (ADR-0004/0005 — 세대를 경계별로 고정).
 *
 * <p>Boot 4의 REST 기본은 Jackson 3(자동구성, camelCase)이며 이 빈들은 REST에
 * 관여하지 않는다. MQ는 Jackson 2 전용 ObjectMapper + Spring AMQP
 * {@link Jackson2JsonMessageConverter}(4.x에서 deprecated지만 존치 — Jackson 3
 * 컨버터 이행은 별도 ADR로 결정한다)를 쓴다.
 *
 * <p>snake_case는 Python 워커·DB 컬럼 관례와의 통일이다. 계약 record에는
 * 애너테이션을 두지 않고 여기 naming strategy가 일괄 적용한다.
 */
@Configuration
public class MqJacksonConfig {

    /** MQ 발행/소비 헤더 — module-event README의 추적 규약. */
    public static final String REQUEST_ID_HEADER = "x-request-id";

    /** MQ 전용 Jackson 2 ObjectMapper. REST의 Jackson 3 자동구성과 무관하다. */
    @Bean
    public ObjectMapper mqObjectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                // java.time을 ISO-8601 문자열로 (occurred_at)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                // 전방 호환 — 상대가 필드를 추가해도 깨지지 않는다 (additive-only의 수신측 절반)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    @Bean
    public Jackson2JsonMessageConverter mqMessageConverter(ObjectMapper mqObjectMapper) {
        return new Jackson2JsonMessageConverter(mqObjectMapper);
    }

    /**
     * MDC의 requestId를 {@code x-request-id} 메시지 헤더로 이어붙이는 RabbitTemplate.
     * AFTER_COMMIT 릴레이는 요청 스레드에서 동기 실행되므로 MDC가 살아 있다.
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         Jackson2JsonMessageConverter mqMessageConverter) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(mqMessageConverter);
        rabbitTemplate.setBeforePublishPostProcessors(message -> {
            String requestId = MDC.get(RequestIdFilter.MDC_KEY);
            if (requestId != null) {
                message.getMessageProperties().setHeader(REQUEST_ID_HEADER, requestId);
            }
            return message;
        });
        return rabbitTemplate;
    }
}
