package com.ssafy.motif.coreapi.domain.order.payment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestToUriTemplate;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 토스 클라이언트의 <b>요청 모양과 실패 분류</b>를 네트워크 없이 고정한다 (API 명세 §12.2).
 *
 * <p>지키려는 계약은 셋이다 — ① Basic 인증은 {@code 시크릿:} 을 인코딩한 값이고 금액은 정수로
 * 나간다, ② 4xx 는 {@link TossApiException}(거절)이되 우리 키 오류·토스 일시 오류는
 * {@link TossUnavailableException} 이다, ③ 5xx·빈 키는 결제 거절로 보이지 않는다. ②③ 이 갈리면
 * 설정 실수 한 번에 손님 주문이 전부 {@code FAILED} 로 기록된다.
 */
class RestClientTossPaymentClientTest {

    private static final String BASE = "https://toss.test";
    private static final String SECRET = "test_sk_secret";
    private static final String ORDER_ID = "e3b0c442-98fc-4c14-a1b2-3d4e5f678901";

    private static final String PAYMENT_JSON = """
            {
              "mId": "tosspayments",
              "version": "2022-11-16",
              "paymentKey": "pk-1",
              "status": "DONE",
              "orderId": "%s",
              "orderName": "카리나 모티프 에셋",
              "totalAmount": 3900,
              "balanceAmount": 3900,
              "method": "카드",
              "approvedAt": "2026-09-10T09:00:00+09:00",
              "card": { "amount": 3900, "issuerCode": "61" }
            }""".formatted(ORDER_ID);

    private MockRestServiceServer server;
    private RestClientTossPaymentClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new RestClientTossPaymentClient(builder.build(), properties(SECRET));
    }

    // --- 승인 --------------------------------------------------------------------

    @Test
    @DisplayName("승인 — Basic(시크릿:) 헤더와 정수 금액으로 POST 하고, 모르는 칸은 무시하며 결제 객체를 읽는다")
    void confirmPostsBasicAuthAndIntegerAmount() {
        server.expect(requestTo(BASE + "/v1/payments/confirm"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, basic(SECRET)))
                .andExpect(header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE))
                .andExpect(request -> assertThat(body(request))
                        .contains("\"paymentKey\":\"pk-1\"")
                        .contains("\"orderId\":\"" + ORDER_ID + "\"")
                        .contains("\"amount\":3900")
                        .doesNotContain("3900.00"))
                .andRespond(withSuccess(PAYMENT_JSON, MediaType.APPLICATION_JSON));

        TossPayment payment = client.confirm("pk-1", ORDER_ID, new BigDecimal("3900.00"));

        assertThat(payment.isDone()).isTrue();
        assertThat(payment.paymentKey()).isEqualTo("pk-1");
        assertThat(payment.orderId()).isEqualTo(ORDER_ID);
        assertThat(payment.totalAmount()).isEqualByComparingTo("3900");
        assertThat(payment.method()).isEqualTo("카드");
        server.verify();
    }

    @Test
    @DisplayName("4xx + {code, message} 는 확정 거절 — TossApiException 에 코드가 실린다")
    void clientErrorWithBodyIsRejection() {
        server.expect(requestTo(BASE + "/v1/payments/confirm"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                { "code": "REJECT_CARD_COMPANY", "message": "카드사에서 승인을 거절했습니다." }"""));

        assertThatThrownBy(() -> client.confirm("pk-1", ORDER_ID, new BigDecimal("3900.00")))
                .isInstanceOfSatisfying(TossApiException.class, e -> {
                    assertThat(e.getHttpStatus()).isEqualTo(403);
                    assertThat(e.getCode()).isEqualTo("REJECT_CARD_COMPANY");
                    assertThat(e.getTossMessage()).isEqualTo("카드사에서 승인을 거절했습니다.");
                });
    }

    @Test
    @DisplayName("본문 없는 4xx 도 거절이다 — 코드는 HTTP 상태로 대신한다")
    void clientErrorWithoutBodyIsRejectionWithStatusCode() {
        server.expect(requestTo(BASE + "/v1/payments/confirm"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> client.confirm("pk-1", ORDER_ID, new BigDecimal("3900")))
                .isInstanceOfSatisfying(TossApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo("HTTP_400"));
    }

    @Test
    @DisplayName("우리 키가 틀린 4xx 는 거절이 아니다 — 결과 불명으로 분류해 주문을 FAILED 로 만들지 않는다")
    void unauthorizedKeyIsNotARejection() {
        server.expect(requestTo(BASE + "/v1/payments/confirm"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                { "code": "UNAUTHORIZED_KEY", "message": "인증되지 않은 시크릿 키 혹은 클라이언트 키 입니다." }"""));

        assertThatThrownBy(() -> client.confirm("pk-1", ORDER_ID, new BigDecimal("3900")))
                .isInstanceOf(TossUnavailableException.class)
                .hasMessageContaining("UNAUTHORIZED_KEY");
    }

    @Test
    @DisplayName("토스의 일시 오류(PROVIDER_ERROR)는 400 이지만 거절이 아니다 — 재시도가 열려야 한다")
    void providerErrorIsNotARejection() {
        server.expect(requestTo(BASE + "/v1/payments/confirm"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                { "code": "PROVIDER_ERROR", "message": "일시적인 오류가 발생했습니다. 잠시 후 다시 시도해주세요." }"""));

        assertThatThrownBy(() -> client.confirm("pk-1", ORDER_ID, new BigDecimal("3900")))
                .isInstanceOf(TossUnavailableException.class);
    }

    @Test
    @DisplayName("5xx 는 결과 불명이다")
    void serverErrorIsUnavailable() {
        server.expect(requestTo(BASE + "/v1/payments/confirm")).andRespond(withServerError());

        assertThatThrownBy(() -> client.confirm("pk-1", ORDER_ID, new BigDecimal("3900")))
                .isInstanceOf(TossUnavailableException.class);
    }

    @Test
    @DisplayName("시크릿 키가 비어 있으면 요청을 만들기 전에 실패한다 — 토스에 빈 키로 가지 않는다")
    void blankSecretFailsBeforeCalling() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        MockRestServiceServer silent = MockRestServiceServer.bindTo(builder).build();
        RestClientTossPaymentClient noKey = new RestClientTossPaymentClient(builder.build(), properties("  "));

        assertThatThrownBy(() -> noKey.confirm("pk-1", ORDER_ID, new BigDecimal("3900")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TOSS_SECRET_KEY");
        silent.verify();   // 기대한 요청이 0건 — 하나라도 나갔으면 여기서 터진다
    }

    // --- 조회 --------------------------------------------------------------------

    @Test
    @DisplayName("조회 — GET /v1/payments/{paymentKey} 에 같은 Basic 헤더로 간다")
    void findGetsByPaymentKey() {
        server.expect(requestToUriTemplate(BASE + "/v1/payments/{paymentKey}", "pk-1"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, basic(SECRET)))
                .andRespond(withSuccess(PAYMENT_JSON, MediaType.APPLICATION_JSON));

        TossPayment payment = client.find("pk-1");

        assertThat(payment.orderId()).isEqualTo(ORDER_ID);
        assertThat(payment.isDone()).isTrue();
        server.verify();
    }

    // --- 금액 --------------------------------------------------------------------

    @Test
    @DisplayName("KRW 스냅샷 3900.00 은 정수 3900 으로 나간다 — 소수부가 남는 금액은 지금 팔지 않는 통화다")
    void amountIsSentAsInteger() {
        assertThat(RestClientTossPaymentClient.toKrw(new BigDecimal("3900.00"))).isEqualTo(3900L);
        assertThat(RestClientTossPaymentClient.toKrw(new BigDecimal("27900"))).isEqualTo(27900L);
        assertThatThrownBy(() -> RestClientTossPaymentClient.toKrw(new BigDecimal("3900.50")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- 도우미 -----------------------------------------------------------------

    private static TossProperties properties(String secretKey) {
        return new TossProperties(BASE, secretKey, Duration.ofSeconds(3), Duration.ofSeconds(10));
    }

    private static String basic(String secret) {
        return "Basic " + Base64.getEncoder().encodeToString((secret + ":").getBytes(StandardCharsets.UTF_8));
    }

    private static String body(ClientHttpRequest request) {
        return ((MockClientHttpRequest) request).getBodyAsString();
    }
}
