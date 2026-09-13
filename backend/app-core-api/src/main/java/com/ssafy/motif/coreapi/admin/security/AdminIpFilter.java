package com.ssafy.motif.coreapi.admin.security;

import com.ssafy.motif.coreapi.admin.AdminPaths;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * 어드민 경로의 네트워크 제한 — 비허용 IP 에 <b>404</b> (명세 §1.1, 회의 확정 R1).
 *
 * <p>403 이 아니라 404 인 것이 요건이다. 403 은 "경로는 있는데 못 들어온다"를 알려줘
 * 어드민 화면의 존재 자체가 드러난다. Traefik {@code ipAllowList} 는 403 만 낼 수 있고
 * {@code errors} 미들웨어도 원래 상태코드를 유지하므로, 이 요건은 앱 필터가 맡는다.
 *
 * <p><b>스프링 빈이다</b> — Security 체인보다 바깥(서블릿 컨테이너)에서 돌아야
 * {@code /admin/**} 전부에 예외 없이 걸린다. 인증 필터와 달리 SecurityContext 를
 * 건드리지 않아 이중 등록 문제가 없다.
 *
 * <p>클라이언트 IP 는 {@code X-Forwarded-For} 첫 값이다. 위조가 통하지 않는 근거는
 * Traefik {@code forwardedHeaders.trustedIPs} 를 k3s 파드·노드 CIDR 로 한정해
 * <b>바깥에서 들어온 헤더를 Traefik 이 버리기 때문</b>이다(§1.1). 이 전제가 깨지면
 * 이 필터도 뚫린다 — 인프라 설정과 한 쌍이다.
 *
 * <p>허용 목록이 비어 있으면 <b>전부 막는다</b>. 설정 누락은 어드민이 잠기는 것으로
 * 드러나야지, 조용히 전 인터넷에 열리면 안 된다.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)   // RequestIdFilter 다음 — 거부 로그에도 requestId 가 붙는다
public class AdminIpFilter extends OncePerRequestFilter {

    private static final String HEADER_FORWARDED_FOR = "X-Forwarded-For";

    private final List<IpAddressMatcher> allowed;

    public AdminIpFilter(AdminProperties properties) {
        List<String> cidrs = properties.allowedCidrs() == null ? List.of() : properties.allowedCidrs();
        // 잘못된 CIDR 은 여기서 기동을 깨뜨린다 — 배포 후 조용히 뚫리는 것보다 낫다
        this.allowed = cidrs.stream().filter(c -> !c.isBlank()).map(IpAddressMatcher::new).toList();
        if (this.allowed.isEmpty()) {
            log.warn("motif.admin.allowed-cidrs 가 비어 있다 — 어드민 경로를 전부 404 로 막는다");
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!AdminPaths.matches(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }
        String clientIp = clientIp(request);
        if (allowed.stream().noneMatch(m -> m.matches(clientIp))) {
            log.warn("어드민 경로 비허용 IP — 404: ip={} uri={}", clientIp, request.getRequestURI());
            // sendError 로 /error 재디스패치 — 존재하지 않는 경로의 404 와 응답이 같아진다
            response.sendError(HttpStatus.NOT_FOUND.value());
            return;
        }
        filterChain.doFilter(request, response);
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader(HEADER_FORWARDED_FOR);
        if (forwarded == null || forwarded.isBlank()) {
            return request.getRemoteAddr();   // 앞단 없는 로컬·테스트 경로
        }
        int comma = forwarded.indexOf(',');
        return (comma < 0 ? forwarded : forwarded.substring(0, comma)).trim();
    }
}
