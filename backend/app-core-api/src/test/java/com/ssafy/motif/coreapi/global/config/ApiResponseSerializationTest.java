package com.ssafy.motif.coreapi.global.config;

import com.ssafy.motif.common.error.CommonErrorCode;
import com.ssafy.motif.common.error.FieldErrorResponse;
import com.ssafy.motif.common.response.ApiResponse;
import com.ssafy.motif.common.response.ApiResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * 공통 응답 봉투가 <b>직렬화된 결과</b>로 명세 §1.2·§1.3 을 지키는지 고정한다.
 *
 * <p>봉투 record 만 단위 테스트하면 시각 포맷(§1.3)을 검증할 수 없다 — 자릿수는
 * Jackson 설정이 정하기 때문이다. 그래서 {@link JacksonConfig} 의 커스터마이저를
 * 실제 {@link JsonMapper} 빌더에 적용해 나온 JSON 문자열을 본다.
 *
 * <p>스프링 컨텍스트를 띄우지 않는다 — DB·MQ·Redis 가 없는 CI 에이전트에서도
 * 도는 기본 {@code test} 태스크에 속해야 하는 계약이다 (build.gradle 의 local-db 분리).
 */
class ApiResponseSerializationTest {

    private final JsonMapper mapper = newMapper();

    private static JsonMapper newMapper() {
        JsonMapperBuilderCustomizer customizer = new JacksonConfig().motifTimeFormatCustomizer();
        JsonMapper.Builder builder = JsonMapper.builder();
        customizer.customize(builder);
        return builder.build();
    }

    @Test
    @DisplayName("성공 봉투는 code=SUCCESS 와 고정 문구를 채운다 (명세 §1.2)")
    void successEnvelopeFillsCodeAndMessage() {
        ApiResponse<String> response = ApiResponse.success("payload");

        assertThat(response.result()).isEqualTo(ApiResult.SUCCESS);
        assertThat(response.code()).isEqualTo("SUCCESS");
        assertThat(response.message()).isEqualTo("요청이 성공했습니다.");
    }

    @Test
    @DisplayName("본문 없는 성공도 봉투를 유지하며 data 는 null 이다 (명세 §1.2)")
    void successWithoutBodyKeepsEnvelope() {
        String json = mapper.writeValueAsString(ApiResponse.success());

        assertThat(json)
                .contains("\"result\":\"SUCCESS\"")
                .contains("\"code\":\"SUCCESS\"")
                .contains("\"data\":null");
    }

    @Test
    @DisplayName("시각은 소수부가 0이어도 밀리초 3자리로 내려간다 (명세 §1.3)")
    void timestampAlwaysHasThreeFractionDigits() {
        ApiResponse<Void> response = new ApiResponse<>(
                ApiResult.SUCCESS, "SUCCESS", "요청이 성공했습니다.", null,
                Instant.parse("2026-09-02T06:00:00Z"));

        assertThat(mapper.writeValueAsString(response))
                .contains("\"timestamp\":\"2026-09-02T06:00:00.000Z\"");
    }

    @Test
    @DisplayName("나노초까지 담긴 시각도 3자리로 잘라 내려간다 — 자릿수가 요청마다 달라지지 않는다")
    void timestampTruncatesSubMilliPrecision() {
        ApiResponse<Void> response = new ApiResponse<>(
                ApiResult.SUCCESS, "SUCCESS", "요청이 성공했습니다.", null,
                Instant.parse("2026-09-02T06:00:00.123456789Z"));

        assertThat(mapper.writeValueAsString(response))
                .contains("\"timestamp\":\"2026-09-02T06:00:00.123Z\"");
    }

    @Test
    @DisplayName("검증 실패 봉투는 INVALID_PARAMETER + 필드 오류 배열이다 (명세 §1.2·§1.4)")
    void validationFailureCarriesFieldErrors() {
        ApiResponse<List<FieldErrorResponse>> response = ApiResponse.fail(
                CommonErrorCode.INVALID_PARAMETER,
                List.of(new FieldErrorResponse("nickname", "2~20자만 허용합니다.")));

        assertThat(mapper.writeValueAsString(response))
                .contains("\"result\":\"FAIL\"")
                .contains("\"code\":\"INVALID_PARAMETER\"")
                .contains("\"field\":\"nickname\"");
    }

    @Test
    @DisplayName("공통 에러 코드는 명세 §1.4 표의 이름·HTTP 상태와 1:1 이다")
    void commonErrorCodesMatchSpecTable() {
        assertThat(CommonErrorCode.values())
                .extracting(CommonErrorCode::getCode, CommonErrorCode::getStatus)
                .contains(
                        tuple("INVALID_PARAMETER", 400),
                        tuple("UNAUTHORIZED", 401),
                        tuple("PAYMENT_FAILED", 402),
                        tuple("FORBIDDEN", 403),
                        tuple("ACCOUNT_SUSPENDED", 403),
                        tuple("ACCOUNT_DELETED", 403),
                        tuple("NOT_FOUND", 404),
                        tuple("CONFLICT", 409),
                        tuple("RATE_LIMITED", 429),
                        tuple("INTERNAL_ERROR", 500));
    }

    @Test
    @DisplayName("에러 코드 문자열은 enum 상수 이름과 같다 — 숫자 코드를 쓰지 않는다")
    void errorCodeStringEqualsEnumName() {
        for (CommonErrorCode errorCode : CommonErrorCode.values()) {
            assertThat(errorCode.getCode()).isEqualTo(errorCode.name());
        }
    }
}
