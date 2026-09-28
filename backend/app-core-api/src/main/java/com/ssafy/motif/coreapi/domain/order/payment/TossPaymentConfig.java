package com.ssafy.motif.coreapi.domain.order.payment;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

/**
 * 토스 클라이언트 구성 — 설정 바인딩과 {@link RestClient} 빈.
 *
 * <p>{@code MediaConfig} 와 같은 모양으로 설정 레코드를 쓰는 패키지에서 직접 켠다.
 *
 * <p><b>이 레포의 첫 외부 HTTP 클라이언트다.</b> 자동구성 빌더에 기대지 않고 직접 만든다 —
 * 타임아웃 두 개가 이 클라이언트의 계약(결과 불명 판정)이라 설정값이 어디서 오는지
 * 파일 하나에서 보여야 한다. 인증 헤더는 여기서 기본값으로 박지 않는다 — 키가 비어 있을
 * 때 기동이 아니라 <b>호출 시점</b>에 실패하게 하려면 요청마다 붙여야 한다.
 */
@Configuration
@EnableConfigurationProperties(TossProperties.class)
public class TossPaymentConfig {

    @Bean
    RestClient tossRestClient(TossProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());
        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }
}
