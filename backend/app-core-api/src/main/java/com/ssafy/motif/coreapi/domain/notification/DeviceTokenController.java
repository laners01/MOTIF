package com.ssafy.motif.coreapi.domain.notification;

import com.ssafy.motif.common.annotation.ApiController;
import com.ssafy.motif.common.response.ApiResponse;
import com.ssafy.motif.coreapi.domain.notification.dto.DeviceTokenRequest;
import com.ssafy.motif.coreapi.global.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** FCM 디바이스 토큰 (API 명세 §14.1·§14.2). */
@Tag(name = "Notification", description = "FCM 토큰 등록·해제 (§14)")
@ApiController
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/notifications/device-tokens")
public class DeviceTokenController {

    private final DeviceTokenService deviceTokenService;

    @Operation(summary = "FCM 토큰 등록",
            description = "로그인 직후·토큰 갱신 시 호출한다. 동일 토큰이 타 사용자 소유였다면 "
                    + "현재 사용자로 재할당하고 다시 활성화한다(upsert).")
    @PostMapping
    public ApiResponse<Void> register(@AuthenticationPrincipal AuthenticatedUser user,
                                      @Valid @RequestBody DeviceTokenRequest request) {
        deviceTokenService.register(user.userSeq(), request);
        return ApiResponse.success();
    }

    @Operation(summary = "FCM 토큰 해제",
            description = "로그아웃 시 현재 기기 토큰을 비활성화한다. 미등록 토큰도 200 이다(멱등). "
                    + "행을 지우지 않고 is_active 를 내린다 — 같은 기기가 다시 로그인하면 되살린다.")
    @DeleteMapping
    public ApiResponse<Void> deactivate(@AuthenticationPrincipal AuthenticatedUser user,
                                        @Valid @RequestBody DeviceTokenRequest request) {
        deviceTokenService.deactivate(user.userSeq(), request);
        return ApiResponse.success();
    }
}
