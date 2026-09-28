package com.ssafy.motif.coreapi.global.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ssafy.motif.coreapi.global.filter.RequestIdFilter;
import com.ssafy.motif.event.contract.CompositeFinishedEvent;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.DefaultJackson2JavaTypeMapper;
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

    /**
     * 발행·소비 공용 컨버터.
     *
     * <p><b>신뢰 패키지를 넣지 않으면 자기가 낸 메시지를 자기가 못 읽는다</b>
     * (2026-09-19, S15P21D104-189 로컬 한 바퀴에서 잡혔다). {@code composite.finished} 는
     * {@code composite.*} 바인딩이라 {@code evt.notification} 과 함께 <b>{@code evt.core}
     * 에도 떨어지는데</b>, 발행측이 {@code __TypeId__} 에 FQCN 을 싣고 수신측
     * {@code DefaultJackson2JavaTypeMapper} 의 기본 신뢰 목록은
     * {@code [java.util, java.lang]} 뿐이라 거부한다.
     *
     * <p><b>리스너가 {@code Message} 를 받아도 소용없다</b> — 실패 지점이 리스너 진입
     * <b>전</b>의 메시지 변환이라 {@code onEvent} 본문은 한 줄도 돌지 않는다. ack 만 하려던
     * 메시지가 재시도 3회 뒤 {@code dlq.evt.core} 에 쌓인다. app-media·app-notification 이
     * 같은 설정을 갖는 이유가 이것이다.
     *
     * <p>{@code *}(전체 신뢰)로 열지 않는다 — 계약 패키지 밖의 클래스가 MQ 로 올 일이 없다.
     * 패키지명은 계약 클래스에서 끌어온다. 문자열로 박으면 패키지를 옮겼을 때 컴파일이
     * 아니라 운영에서 터진다.
     */
    @Bean
    public Jackson2JsonMessageConverter mqMessageConverter(ObjectMapper mqObjectMapper) {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter(mqObjectMapper);
        DefaultJackson2JavaTypeMapper typeMapper = new DefaultJackson2JavaTypeMapper();
        typeMapper.setTrustedPackages(CompositeFinishedEvent.class.getPackageName());
        converter.setJavaTypeMapper(typeMapper);
        return converter;
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
