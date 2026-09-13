package com.ssafy.motif.media.global.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.boot.amqp.autoconfigure.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MQ 소비 구성 — app-notification·core-api와 같은 사양의 Jackson 2 컨버터 (ADR-0004/0005).
 *
 * <ul>
 *   <li>{@code defaultRequeueRejected=false}: 실패 메시지를 requeue 하지 않는다 —
 *       포이즌 메시지가 큐 머리에서 무한 회전하는 것을 막는다.</li>
 *   <li>stateless retry 3회(지수 백오프) 소진 시
 *       {@link RejectAndDontRequeueRecoverer} → DLX(motif.dlx) → dlq.job.media 격리.
 *       (Spring AMQP 4.x는 spring-retry 대신 Framework 코어 retry를 쓴다 —
 *       {@code maxRetries}가 구 {@code maxAttempts} 상당.)</li>
 *   <li>컨테이너 팩토리는 Boot configurer로 yml 리스너 설정을 승계한 뒤 덮어쓴다 —
 *       local-remote의 {@code listener.simple.auto-startup=false}(ADR-0003
 *       하이브리드 규칙: 서버 큐를 로컬이 삼키지 않는다)가 그대로 적용되게 한다.</li>
 * </ul>
 */
@Configuration
public class MqConfig {

    /** MQ 전용 Jackson 2 ObjectMapper — REST(Jackson 3)와 세대 분리 (ADR-0004). */
    @Bean
    public ObjectMapper mqObjectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    @Bean
    public Jackson2JsonMessageConverter mqMessageConverter(ObjectMapper mqObjectMapper) {
        return new Jackson2JsonMessageConverter(mqObjectMapper);
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ConnectionFactory connectionFactory,
            Jackson2JsonMessageConverter mqMessageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setMessageConverter(mqMessageConverter);
        factory.setDefaultRequeueRejected(false);
        factory.setAdviceChain(RetryInterceptorBuilder.stateless()
                .maxRetries(3)                     // 재시도 3회 (최초 시도 제외)
                .backOffOptions(1000, 2.0, 10000)  // 1s → 2s → 4s (상한 10s)
                .recoverer(new RejectAndDontRequeueRecoverer())
                .build());
        return factory;
    }
}
