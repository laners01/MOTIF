package com.ssafy.motif.coreapi.domain.order.payment;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 토스페이먼츠 코어 API 접속 설정 (API 명세 §12 — 토스 승인형).
 *
 * <p><b>{@code secretKey} 가 비어 있어도 앱은 뜬다.</b> 미설정이면 결제 승인(§12.2)만
 * 호출 시점에 500 으로 막힌다({@code RestClientTossPaymentClient}) — {@code CLOUDFRONT_PRIVATE_KEY}
 * 와 같은 규칙이다. 결제 키가 없다고 기동을 막으면 결제와 무관한 기능 개발·시연이 함께 막힌다.
 * 운영 값은 {@code core-api-secret} 의 {@code TOSS_SECRET_KEY} 다(application-prod.yml).
 *
 * @param baseUrl        {@code https://api.tosspayments.com} — 테스트 키({@code test_sk_…})도 같은
 *                       호스트다. 바꾸는 경우는 로컬 스텁뿐이다
 * @param secretKey      시크릿 키. Basic 인증의 사용자명 자리에 들어가고 비밀번호는 빈 값이다
 * @param connectTimeout 연결 타임아웃
 * @param readTimeout    응답 타임아웃 — 승인은 카드사 왕복이 있어 길게 둔다. 넘기면 결과 불명으로
 *                       보고 주문을 {@code PENDING} 에 남긴다
 */
@ConfigurationProperties(prefix = "motif.payment.toss")
public record TossProperties(String baseUrl, String secretKey, Duration connectTimeout, Duration readTimeout) {

    public boolean hasSecretKey() {
        return secretKey != null && !secretKey.isBlank();
    }
}
