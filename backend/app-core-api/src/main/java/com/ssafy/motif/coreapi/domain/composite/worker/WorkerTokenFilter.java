package com.ssafy.motif.coreapi.domain.composite.worker;

import com.ssafy.motif.common.error.CommonErrorCode;
import com.ssafy.motif.coreapi.domain.composite.CompositeProperties;
import com.ssafy.motif.coreapi.global.security.SecurityErrorWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 워커 콜백 인증 — 공유 비밀 하나 (작업명세 §4.3).
 *
 * <p><b>JWT 가 아니다.</b> 상대가 우리 사용자도 어드민도 아닌 <b>AI 팀의 Cloud Run
 * Job</b> 이라 발급·갱신할 주체가 없다. 비밀 하나를 양쪽 Secret 에 두고 대조한다.
 *
 * <p><b>{@code MessageDigest.isEqual} 로 비교한다.</b> {@code String.equals} 는 첫 다른
 * 바이트에서 즉시 빠져나와 <b>비교 시간이 일치한 접두사 길이에 비례</b>한다 — 토큰을
 * 한 글자씩 늘려가며 응답 시간을 재면 값을 복원할 수 있다. 비밀 비교에 쓰면 안 된다.
 *
 * <p><b>토큰이 설정되지 않았으면 전부 401 이다</b>(fail-closed). 빈 값을 "검사 생략" 으로
 * 읽으면 설정 실수 하나로 콜백이 인터넷에 열리고, 아무나 남의 잡을 {@code DONE} 으로
 * 바꾸거나 {@code FAILED} 로 닫을 수 있다.
 *
 * <p><b>스프링 빈이 아니다.</b> {@code Filter} 를 빈으로 두면 Boot 가 서블릿 컨테이너
 * 체인에도 자동 등록해 Security 체인 밖에서 한 번 더 돈다({@code AdminAuthFilter} javadoc).
 * {@link WorkerSecurityConfig} 가 직접 생성한다.
 *
 * <p>여기서 바로 401 봉투를 쓴다 — 익명 통과시킨 뒤 entry point 에 맡기는 어드민 체인과
 * 다른 점이다. 이 경로에는 "로그인 화면" 같은 예외가 없어 통과시킬 이유가 없다.
 */
@Slf4j
@RequiredArgsConstructor
public class WorkerTokenFilter extends OncePerRequestFilter {

    private final CompositeProperties properties;
    private final SecurityErrorWriter errorWriter;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!authorized(request.getHeader(WorkerHeaders.TOKEN))) {
            log.warn("워커 콜백 토큰 불일치 — {} {}", request.getMethod(), request.getRequestURI());
            errorWriter.write(response, CommonErrorCode.UNAUTHORIZED);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean authorized(String presented) {
        if (!properties.hasWorkerToken()) {
            log.error("motif.composite.worker-token 이 비어 있다 — 워커 콜백을 전부 막는다");
            return false;
        }
        if (presented == null) {
            return false;
        }
        return MessageDigest.isEqual(
                presented.getBytes(StandardCharsets.UTF_8),
                properties.workerToken().getBytes(StandardCharsets.UTF_8));
    }
}
