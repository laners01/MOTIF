package com.ssafy.motif.coreapi.global.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * {@code X-Request-Id} 승계 또는 생성 → MDC·응답 헤더, 그리고 <b>액세스 로그</b>
 * (MODERA 관례 계승 — ADR-0005).
 *
 * <p>게이트웨이/클라이언트가 보낸 값을 승계하고, 없으면 UUID 앞 8자를 만든다.
 * MQ 발행 시에는 {@code MqJacksonConfig}의 RabbitTemplate이 MDC의 이 값을
 * {@code x-request-id} 메시지 헤더로 이어붙인다 — 잡 하나를 API → 큐 → 워커 →
 * 알림까지 단일 ID로 추적한다.
 *
 * <p><b>가장 먼저 돌아야 한다</b>({@code HIGHEST_PRECEDENCE}). Security 가 막은
 * 401/403 응답에도 requestId 가 붙고 액세스 로그가 남아야 하기 때문이다 — 인증
 * 실패야말로 기록이 필요한 자리다.
 *
 * <p><b>관리 포트(9081)에는 걸리지 않는다.</b> actuator 는 별도 서블릿 컨텍스트라
 * 이 빈이 적용되지 않는다. 그래서 프로브 로그의 requestId 는 비어 있다 — 정상이다.
 * {@link #logAccess} 의 {@code /actuator} 제외는 관리 포트를 본체 포트로 열었을 때를
 * 대비한 이중 방어다.
 *
 * <p>── 무엇을 찍고 무엇을 안 찍는가 ──────────────────────────────────────────
 *
 * <p>찍는 것은 {@code method · path · status · 소요ms · 클라이언트 IP} 다.
 * <b>쿼리스트링과 본문은 찍지 않는다.</b> 구글 ID 토큰·JWT·닉네임이 그 자리로
 * 지나가므로, 통째로 남기면 로그가 곧 개인정보 저장소가 된다. 경로 변수(seq·uuid)는
 * 식별자일 뿐이라 남긴다.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-Request-Id";
    public static final String MDC_KEY = "requestId";

    private static final String ACTUATOR_PATH_PREFIX = "/actuator";
    private static final int GENERATED_ID_LENGTH = 8;

    /**
     * 이 시간을 넘으면 정상 응답이어도 {@code warn} 이다.
     *
     * <p>MODERA 와 같은 값이다. 어드민 등록처럼 S3 HEAD 를 여러 번 치는 경로는 이 선을
     * 넘을 수 있는데, <b>그것을 보려고 둔 값이다</b> — 느려진 것을 나중에 알게 되는
     * 것보다 지금 시끄러운 편이 낫다. 잡음이 실제로 거슬리면 그때 올린다.
     */
    private static final long SLOW_REQUEST_MILLIS = 300L;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String requestId = request.getHeader(HEADER_NAME);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString().substring(0, GENERATED_ID_LENGTH);
        }
        MDC.put(MDC_KEY, requestId);
        response.setHeader(HEADER_NAME, requestId);
        long startedAt = System.currentTimeMillis();
        try {
            filterChain.doFilter(request, response);
        } finally {
            // MDC.remove 앞이어야 이 줄에도 requestId 가 붙는다. finally 인 것도 의도다 —
            // 예외로 빠져나가는 요청이야말로 기록이 필요하다
            logAccess(request, response, System.currentTimeMillis() - startedAt);
            MDC.remove(MDC_KEY);
        }
    }

    /**
     * 상태와 소요 시간으로 레벨을 가른다.
     *
     * <p><b>정상 요청도 {@code info} 다.</b> MODERA 는 {@code debug} 였으나, 그러면
     * "요청이 앱까지 왔는지" 를 운영에서 확인할 수 없다 — 2026-09-15 에 실제로 그 상황을
     * 겪었고, 확인하려고 {@code org.springframework.web} 을 통째로 DEBUG 로 올렸다가
     * 10초마다 오는 헬스 프로브에 파묻혔다. 트래픽 규모상 감당되는 값이라 {@code info}
     * 로 둔다. 시끄러워지면 {@code application-prod.yml} 에서 이 로거만 내린다.
     */
    void logAccess(HttpServletRequest request, HttpServletResponse response, long elapsedMs) {
        String uri = request.getRequestURI();
        if (uri.startsWith(ACTUATOR_PATH_PREFIX)) {
            return;
        }
        String method = request.getMethod();
        int status = response.getStatus();
        String ip = ClientIp.of(request);

        if (status >= 500) {
            log.error("{} {} {} {}ms ip={}", method, uri, status, elapsedMs, ip);
        } else if (status >= 400 || elapsedMs >= SLOW_REQUEST_MILLIS) {
            log.warn("{} {} {} {}ms ip={}", method, uri, status, elapsedMs, ip);
        } else {
            log.info("{} {} {} {}ms ip={}", method, uri, status, elapsedMs, ip);
        }
    }
}
