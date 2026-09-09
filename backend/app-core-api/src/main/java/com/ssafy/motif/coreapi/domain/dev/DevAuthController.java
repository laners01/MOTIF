package com.ssafy.motif.coreapi.domain.dev;

import com.ssafy.motif.common.annotation.ApiController;
import com.ssafy.motif.common.response.ApiResponse;
import com.ssafy.motif.coreapi.domain.auth.dto.LoginResponse;
import com.ssafy.motif.coreapi.domain.auth.service.GoogleLoginProcessor;
import com.ssafy.motif.coreapi.global.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.annotation.PostConstruct;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 구글을 거치지 않는 개발용 로그인 (격리 모드 — ADR-0003, {@code DevJobController} 와 같은 패턴).
 *
 * <pre>
 * POST /api/dev/auth/login {"oauthSubject":"dev-1"}
 *   → §2.1 과 동일한 토큰 쌍·isNewUser 를 돌려준다. 구글 왕복만 건너뛴다.
 * </pre>
 *
 * <p><b>이것은 토큰 발급 백도어다.</b> 구글 검증을 건너뛰고 임의 사용자의 access token
 * 을 만들어 준다. 안전장치는 두 개다 — {@code @Profile("local")} 이라 다른 프로파일에서는
 * 빈 자체가 만들어지지 않고(운영은 {@code prod}), {@code @Hidden} 이라 문서에도 없다.
 * <b>프로파일 조건을 넓히거나 제거하면 안 된다.</b> 기동 시 WARN 을 남겨 어느 실행체에
 * 열려 있는지 로그로 드러낸다.
 *
 * <p>구글 ID 토큰 검증 경로 자체는 이 엔드포인트로 확인할 수 없다 —
 * {@code backend/local-infra/google-login-test.html} 로 실제 토큰을 받아 §2.1 을 호출한다.
 */
@Slf4j
@Hidden
@Profile("local")
@ApiController
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/dev/auth")
public class DevAuthController {

    private final GoogleLoginProcessor googleLoginProcessor;

    @PostConstruct
    void warnEnabled() {
        log.warn("개발용 로그인 엔드포인트가 열렸다 — POST /api/dev/auth/login "
                + "(구글 검증을 건너뛴다. local 프로파일 전용)");
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> devLogin(@Valid @RequestBody DevLoginRequest request) {
        log.warn("개발용 로그인 사용: oauthSubject={}", request.oauthSubject());
        return ApiResponse.success(googleLoginProcessor.process(request.oauthSubject()));
    }

    /**
     * 보낸 access token 이 무엇으로 해석되는지 그대로 돌려준다 — 토큰 디버깅용.
     *
     * <p>이 경로 자체는 permitAll 이라 토큰 없이도 200 이다. 인증 여부는 응답 내용으로
     * 판단한다({@code authenticated: false} 면 필터가 principal 을 못 채웠다는 뜻).
     */
    @GetMapping("/whoami")
    public ApiResponse<Map<String, Object>> whoami() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Object principal = authentication == null ? null : authentication.getPrincipal();
        if (!(principal instanceof AuthenticatedUser user)) {
            return ApiResponse.success(Map.of("authenticated", false));
        }
        return ApiResponse.success(Map.of(
                "authenticated", true,
                "userSeq", user.userSeq(),
                "role", user.role().name(),
                "authorities", authentication.getAuthorities().toString()));
    }
}
