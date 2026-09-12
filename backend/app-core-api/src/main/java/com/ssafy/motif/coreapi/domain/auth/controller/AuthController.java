package com.ssafy.motif.coreapi.domain.auth.controller;

import com.ssafy.motif.common.annotation.ApiController;
import com.ssafy.motif.common.response.ApiResponse;
import com.ssafy.motif.coreapi.domain.auth.docs.AuthControllerDocs;
import com.ssafy.motif.coreapi.domain.auth.dto.GoogleLoginRequest;
import com.ssafy.motif.coreapi.domain.auth.dto.LoginResponse;
import com.ssafy.motif.coreapi.domain.auth.dto.TokenRefreshRequest;
import com.ssafy.motif.coreapi.domain.auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 인증 API (명세 §2). Base URL 은 {@code /api/v1} (명세 §1.1).
 *
 * <p>Swagger 문서는 {@link AuthControllerDocs} 가 갖는다 — 여기에는 매핑과 코드만 둔다.
 *
 * <p>이 경로는 인증 예외다 — {@code SecurityConfig} 의 permitAll 목록 참조.
 */
@ApiController
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
public class AuthController implements AuthControllerDocs {

    private final AuthService authService;

    /** §2.1 구글 로그인·자동 회원가입. */
    @Override
    @PostMapping("/login/google")
    public ApiResponse<LoginResponse> loginWithGoogle(@Valid @RequestBody GoogleLoginRequest request) {
        return ApiResponse.success(authService.loginWithGoogle(request.idToken()));
    }

    /** §2.2 토큰 재발급 (rotation). */
    @Override
    @PostMapping("/refresh")
    public ApiResponse<LoginResponse> refresh(@Valid @RequestBody TokenRefreshRequest request) {
        return ApiResponse.success(authService.refresh(request.refreshToken()));
    }
}
