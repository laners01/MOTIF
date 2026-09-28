package com.ssafy.motif.coreapi.global.config;

import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.boot.amqp.autoconfigure.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * {@code evt.core} 소비 구성 (S15P21D104-189) — app-media·app-notification 과 같은 사양.
 *
 * <p><b>2026-09-19 이전 core-api 에는 {@code @RabbitListener} 가 하나도 없었다.</b>
 * 발행만 했으므로 컨테이너 팩토리도 없었고, app-media 가 {@code composite.packaged} 를
 * 보내도 받을 곳이 없었다(작업명세 §1 "없는 것").
 *
 * <ul>
 *   <li>{@code defaultRequeueRejected=false}: 포이즌 메시지가 큐 머리에서 무한 회전하는
 *       것을 막는다.</li>
 *   <li>재시도 3회(지수 백오프) 소진 시 {@link RejectAndDontRequeueRecoverer} →
 *       {@code motif.dlx} → {@code dlq.evt.core} 격리.</li>
 *   <li>Boot configurer 로 yml 리스너 설정을 승계한 뒤 덮어쓴다 — local-remote 의
 *       {@code listener.simple.auto-startup=false}(ADR-0003: 서버 큐를 로컬이 삼키지
 *       않는다)가 그대로 적용되게 한다.</li>
 * </ul>
 *
 * <p>컨버터는 발행 경로와 같은 {@code mqMessageConverter} 를 물린다 — <b>신뢰 패키지가
 * 거기 들어 있어야 한다</b>({@code MqJacksonConfig}). 리스너가 {@code Message} 를 받아
 * 직접 푸는 것과 별개로, 변환은 리스너 진입 전에 먼저 돈다.
 */
@Configuration
public class MqListenerConfig {

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
