package com.ssafy.motif.coreapi.domain.composite;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

/**
 * AI 접수 클라이언트 구성 — {@code TossPaymentConfig} 와 같은 모양이다.
 *
 * <p>자동구성 빌더에 기대지 않고 직접 만든다. 타임아웃 두 개가 이 클라이언트의 계약이라
 * (넘기면 "답을 못 받았다" 로 보고 잡을 {@code QUEUED} 에 남긴다) 설정값이 어디서 오는지
 * 파일 하나에서 보여야 한다.
 *
 * <p><b>{@code baseUrl} 을 박지 않는다.</b> 접수 URL 이 통째로 설정값이고(Cloud Run 주소는
 * 배포마다 바뀐다) 경로가 하나뿐이라, 호출부가 전체 URL 을 그대로 쓴다. 토큰 헤더도
 * 기본값으로 박지 않는다 — 비어 있을 때 기동이 아니라 호출 시점에 걸러야 한다.
 */
@Configuration
@EnableConfigurationProperties(CompositeProperties.class)
public class CompositeConfig {

    @Bean
    RestClient compositeSubmitRestClient(CompositeProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());
        return RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }
}
