package com.ssafy.motif.coreapi.global.security;

import com.ssafy.motif.common.error.ErrorCode;
import com.ssafy.motif.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 필터 단계의 실패 응답을 {@link ApiResponse} 봉투로 직렬화한다.
 *
 * <p>전역 핸들러({@code @RestControllerAdvice})는 컨트롤러 진입 이후만 감싸므로,
 * 인증·인가 거부는 여기서 같은 봉투를 직접 만들어야 형식이 갈라지지 않는다.
 *
 * <p>ObjectMapper는 REST 자동구성의 <b>Jackson 3</b>({@code tools.jackson})다 —
 * MQ 전용 Jackson 2 빈과 타입이 달라 주입이 섞이지 않는다 (ADR-0004).
 */
@Component
@RequiredArgsConstructor
public class SecurityErrorWriter {

    private final ObjectMapper objectMapper;

    public void write(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        response.setStatus(errorCode.getStatus());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), ApiResponse.fail(errorCode));
    }
}
