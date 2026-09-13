package com.ssafy.motif.coreapi.admin.auth;

import com.ssafy.motif.common.annotation.ApiController;
import com.ssafy.motif.common.response.ApiResponse;
import com.ssafy.motif.coreapi.admin.AdminPaths;
import com.ssafy.motif.coreapi.admin.auth.dto.AdminLoginRequest;
import com.ssafy.motif.coreapi.admin.auth.dto.AdminLoginResponse;
import com.ssafy.motif.coreapi.admin.auth.dto.AdminMeResponse;
import com.ssafy.motif.coreapi.admin.security.AuthenticatedAdmin;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletResponse;

/**
 * 어드민 인증 API (어드민 명세 §3.1).
 *
 * <p>로그인만 {@code permitAll} 이다 — {@code AdminSecurityConfig} 가 경로로 연다.
 * 로그아웃·{@code /me} 는 인증이 필요하다: 토큰 없이 부를 일이 없고, 열어 두면
 * 미인증 요청으로 경로 존재를 확인할 수 있게 된다.
 *
 * <p>IP 제한은 이 컨트롤러 <b>바깥</b>에서 이미 끝났다({@code AdminIpFilter}) —
 * 허용 대역 밖에서는 로그인 경로조차 404 다(§1.1).
 *
 * <p>쿠키는 {@code ApiResponse} 봉투에 담을 수 없어 {@code HttpServletResponse} 에
 * 직접 싣는다. 봉투 규약(§1.5)을 깨지 않으면서 헤더를 더하는 가장 작은 방법이다.
 */
@Tag(name = "Admin Auth", description = "어드민 로그인·로그아웃 (어드민 명세 §3.1)")
@ApiController
@RestController
@RequiredArgsConstructor
@RequestMapping(AdminPaths.API + "/auth")
public class AdminAuthController {

    private final AdminAuthService adminAuthService;
    private final AdminSessionCookie sessionCookie;

    @Operation(summary = "로그인",
            description = "성공 시 motif_admin 쿠키(화면용)와 accessToken(도구용)을 함께 준다. "
                    + "ID·PW 불일치는 401 ADMIN_LOGIN_FAILED 로 합쳐 응답한다(계정 존재 노출 방지).")
    @PostMapping("/login")
    public ApiResponse<AdminLoginResponse> login(@Valid @RequestBody AdminLoginRequest request,
                                                 HttpServletResponse response) {
        AdminLoginResponse login = adminAuthService.login(request);
        response.addHeader(HttpHeaders.SET_COOKIE, sessionCookie.issue(login.accessToken()).toString());
        return ApiResponse.success(login);
    }

    @Operation(summary = "로그아웃",
            description = "쿠키만 지운다. 서버 측 폐기 목록이 없어 이미 발급된 토큰은 만료까지 유효하다(§1.2).")
    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, sessionCookie.clear().toString());
        return ApiResponse.success();
    }

    @Operation(summary = "현재 어드민", description = "토큰 클레임을 그대로 돌려준다 — admins 를 다시 읽지 않는다.")
    @GetMapping("/me")
    public ApiResponse<AdminMeResponse> me(@AuthenticationPrincipal AuthenticatedAdmin admin) {
        return ApiResponse.success(AdminMeResponse.from(admin));
    }
}
