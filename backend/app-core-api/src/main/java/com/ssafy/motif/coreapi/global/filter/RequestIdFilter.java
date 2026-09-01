package com.ssafy.motif.coreapi.global.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * {@code X-Request-Id} 승계 또는 생성 → MDC·응답 헤더 (MODERA 관례 계승 — ADR-0005).
 *
 * <p>게이트웨이/클라이언트가 보낸 값을 승계하고, 없으면 UUID 앞 8자를 만든다.
 * MQ 발행 시에는 {@code MqJacksonConfig}의 RabbitTemplate이 MDC의 이 값을
 * {@code x-request-id} 메시지 헤더로 이어붙인다 — 잡 하나를 API → 큐 → 워커 →
 * 알림까지 단일 ID로 추적한다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-Request-Id";
    public static final String MDC_KEY = "requestId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String requestId = request.getHeader(HEADER_NAME);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString().substring(0, 8);
        }
        MDC.put(MDC_KEY, requestId);
        response.setHeader(HEADER_NAME, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
