package com.ssafy.motif.coreapi.domain.order.payment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Set;
import java.util.function.Supplier;

/**
 * {@link TossPaymentClient} 의 {@link RestClient} 구현.
 *
 * <p><b>4xx 만 거절이다.</b> 토스는 카드사 거절·세션 만료·중복 처리 같은 "이 결제는 안 된다"를
 * 4xx 와 {@code {code, message}} 본문으로 답한다. 5xx·타임아웃·연결 실패는 답을 못 받은 것이고,
 * 4xx 중에서도 우리 키가 틀린 것({@code UNAUTHORIZED_KEY} …)과 토스가 "잠시 후 다시"라고 한
 * {@code PROVIDER_ERROR} 는 고객의 결제가 거절된 것이 아니라 {@link TossUnavailableException}
 * 으로 낸다 — 그쪽으로 가면 주문이 {@code FAILED} 가 되지 않고 재시도가 열린다.
 *
 * <p><b>금액은 정수다.</b> 우리 스냅샷은 {@code NUMERIC(10,2)} 지만 토스 {@code amount} 는
 * 통화 단위 정수이고 KRW 는 소수가 없다. {@code 3900.00} → {@code 3900} 이며, 소수부가 남는
 * 금액은 지금 팔지 않는 통화라 예외다.
 *
 * <p>시크릿 키가 비어 있으면 <b>요청을 만들기 전에</b> {@link IllegalStateException} 이다 —
 * 빈 키로 토스를 부르면 {@code UNAUTHORIZED_KEY} 가 돌아와 결제 거절처럼 보인다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RestClientTossPaymentClient implements TossPaymentClient {

    /** 4xx 지만 고객의 결제가 거절된 것이 아닌 코드 — 우리 설정 문제거나 토스의 일시 오류다. */
    static final Set<String> NOT_A_REJECTION = Set.of(
            "UNAUTHORIZED_KEY", "INCORRECT_BASIC_AUTH_FORMAT", "FORBIDDEN_REQUEST", "PROVIDER_ERROR");

    private final RestClient tossRestClient;
    private final TossProperties properties;

    @Override
    public TossPayment confirm(String paymentKey, String orderId, BigDecimal amount) {
        ConfirmBody body = new ConfirmBody(paymentKey, orderId, toKrw(amount));
        return call("confirm", () -> tossRestClient.post()
                .uri("/v1/payments/confirm")
                .header(HttpHeaders.AUTHORIZATION, basicAuth())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(TossPayment.class));
    }

    @Override
    public TossPayment find(String paymentKey) {
        return call("find", () -> tossRestClient.get()
                .uri("/v1/payments/{paymentKey}", paymentKey)
                .header(HttpHeaders.AUTHORIZATION, basicAuth())
                .retrieve()
                .body(TossPayment.class));
    }

    private TossPayment call(String what, Supplier<TossPayment> request) {
        try {
            TossPayment payment = request.get();
            if (payment == null) {
                throw new TossUnavailableException("toss " + what + ": 빈 응답", null);
            }
            return payment;
        } catch (HttpClientErrorException e) {
            TossError error = errorBody(e);
            if (NOT_A_REJECTION.contains(error.code())) {
                log.warn("토스 {} 호출이 설정·일시 오류로 실패 — {} {}: {}",
                        what, e.getStatusCode().value(), error.code(), error.message());
                throw new TossUnavailableException(
                        "toss " + what + " " + e.getStatusCode().value() + " " + error.code(), e);
            }
            throw new TossApiException(e.getStatusCode().value(), error.code(), error.message());
        } catch (RestClientException e) {
            // 5xx(HttpServerErrorException) · 타임아웃·연결 실패(ResourceAccessException) ·
            // 해석 불가 응답 — 전부 "답을 못 받았다"
            log.warn("토스 {} 호출 결과 불명 — {}", what, e.getMessage());
            throw new TossUnavailableException("toss " + what + ": " + e.getMessage(), e);
        }
    }

    /** 오류 본문 {@code {code, message}} — 본문이 없거나 JSON 이 아니면 상태 문구로 대신한다. */
    private static TossError errorBody(HttpClientErrorException e) {
        try {
            TossError error = e.getResponseBodyAs(TossError.class);
            if (error != null && error.code() != null) {
                return error;
            }
        } catch (RuntimeException ignored) {
            // 본문 해석 실패 — 아래 대체값
        }
        return new TossError("HTTP_" + e.getStatusCode().value(), e.getStatusText());
    }

    private String basicAuth() {
        if (!properties.hasSecretKey()) {
            throw new IllegalStateException(
                    "TOSS_SECRET_KEY 가 설정되지 않았다 — 결제 승인을 호출할 수 없다 (motif.payment.toss.secret-key)");
        }
        // 토스 Basic 인증 — 사용자명이 시크릿 키, 비밀번호는 빈 값. 콜론까지 포함해 인코딩한다
        String token = Base64.getEncoder()
                .encodeToString((properties.secretKey() + ":").getBytes(StandardCharsets.UTF_8));
        return "Basic " + token;
    }

    /** {@code 3900.00} → {@code 3900}. 소수부가 남으면 KRW 가 아니다 — 지금은 팔지 않는 통화다. */
    static long toKrw(BigDecimal amount) {
        try {
            return amount.stripTrailingZeros().longValueExact();
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("토스에는 정수 금액만 보낸다 — amount: " + amount, e);
        }
    }

    /** {@code POST /v1/payments/confirm} 본문 — 토스 문서의 세 필드 그대로. */
    record ConfirmBody(String paymentKey, String orderId, long amount) {
    }

    /** 토스 오류 응답 본문. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record TossError(String code, String message) {
    }
}
