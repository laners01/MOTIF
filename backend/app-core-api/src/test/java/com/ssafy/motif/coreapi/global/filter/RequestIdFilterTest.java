package com.ssafy.motif.coreapi.global.filter;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 액세스 로그 (ADR-0005 · MODERA 관례 계승).
 *
 * <p>레벨 분기는 <b>틀려도 아무 증상이 없다</b> — 5xx 가 info 로 찍히면 장애가 조용히
 * 지나가고, 정상 요청이 error 면 알림이 울어대다 무시하게 된다. 그래서 고정한다.
 */
class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();
    private ListAppender<ILoggingEvent> appender;
    private Logger logger;

    @BeforeEach
    void attach() {
        logger = (Logger) LoggerFactory.getLogger(RequestIdFilter.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.TRACE);
    }

    @AfterEach
    void detach() {
        logger.detachAppender(appender);
    }

    private void call(String uri, int status, long elapsedMs) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(status);
        filter.logAccess(request, response, elapsedMs);
    }

    private ILoggingEvent only() {
        assertThat(appender.list).hasSize(1);
        return appender.list.get(0);
    }

    @Test
    @DisplayName("정상 요청도 info 로 남긴다 — 요청이 앱까지 왔는지를 운영에서 볼 수 있어야 한다")
    void 정상은_info() {
        call("/api/v1/challenges/feed", 200, 10);

        assertThat(only().getLevel()).isEqualTo(Level.INFO);
        assertThat(only().getFormattedMessage()).contains("GET", "/api/v1/challenges/feed", "200", "10ms");
    }

    @Test
    @DisplayName("4xx 는 warn — 인증 실패야말로 기록이 필요한 자리다")
    void 클라이언트_오류는_warn() {
        call("/api/v1/artists/1", 401, 5);

        assertThat(only().getLevel()).isEqualTo(Level.WARN);
    }

    @Test
    @DisplayName("5xx 는 error")
    void 서버_오류는_error() {
        call("/api/v1/challenges/feed", 500, 5);

        assertThat(only().getLevel()).isEqualTo(Level.ERROR);
    }

    @Test
    @DisplayName("300ms 를 넘으면 성공이어도 warn — 느려진 것을 나중에 알게 되지 않는다")
    void 느린_성공도_warn() {
        call("/api/v1/admin/challenges", 200, 300);

        assertThat(only().getLevel()).isEqualTo(Level.WARN);
    }

    @Test
    @DisplayName("actuator 는 찍지 않는다 — 프로브가 10초마다 와서 다른 로그를 덮는다")
    void actuator_는_제외() {
        call("/actuator/health/liveness", 200, 1);

        assertThat(appender.list).isEmpty();
    }

    @Test
    @DisplayName("쿼리스트링은 남기지 않는다 — 토큰·개인정보가 그 자리로 지나간다")
    void 쿼리스트링_미노출() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/artists");
        request.setQueryString("idToken=eyJhbGciOi.SECRET.sig&q=홍길동");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);

        filter.logAccess(request, response, 1);

        assertThat(only().getFormattedMessage()).doesNotContain("SECRET", "idToken", "홍길동");
    }

    @Test
    @DisplayName("클라이언트 IP 는 X-Forwarded-For 첫 값 — AdminIpFilter 가 막는 값과 같아야 한다")
    void 출발지_IP() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/artists");
        request.addHeader("X-Forwarded-For", "14.46.141.178, 10.42.0.8");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);

        filter.logAccess(request, response, 1);

        assertThat(only().getFormattedMessage()).contains("ip=14.46.141.178");
    }
}
