package com.ssafy.motif.coreapi.auth;

import com.ssafy.motif.common.annotation.ApiController;
import com.ssafy.motif.common.response.ApiResponse;
import com.ssafy.motif.coreapi.auth.dto.GoogleLoginRequest;
import com.ssafy.motif.coreapi.auth.dto.LoginResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 인증 API (명세 §2). Base URL은 {@code /api/v1} (명세 §1.1).
 *
 * <p>이 경로는 인증 예외다 — {@code SecurityConfig}의 permitAll 목록 참조.
 */
@ApiController
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    /** §2.1 구글 로그인·자동 회원가입. */
    @PostMapping("/login/google")
    public ApiResponse<LoginResponse> loginWithGoogle(@Valid @RequestBody GoogleLoginRequest request) {
        return ApiResponse.success(authService.loginWithGoogle(request.idToken()));
    }
}
