package com.ssafy.motif.coreapi.domain.notification;

import com.ssafy.motif.common.annotation.ApiController;
import com.ssafy.motif.common.response.ApiResponse;
import com.ssafy.motif.coreapi.domain.notification.docs.DeviceTokenControllerDocs;
import com.ssafy.motif.coreapi.domain.notification.dto.DeviceTokenRequest;
import com.ssafy.motif.coreapi.global.security.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** FCM 디바이스 토큰 (API 명세 §14.1·§14.2). 문서는 {@link DeviceTokenControllerDocs}. */
@ApiController
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/notifications/device-tokens")
public class DeviceTokenController implements DeviceTokenControllerDocs {

    private final DeviceTokenService deviceTokenService;

    @Override
    @PostMapping
    public ApiResponse<Void> register(@AuthenticationPrincipal AuthenticatedUser user,
                                      @Valid @RequestBody DeviceTokenRequest request) {
        deviceTokenService.register(user.userSeq(), request);
        return ApiResponse.success();
    }

    @Override
    @DeleteMapping
    public ApiResponse<Void> deactivate(@AuthenticationPrincipal AuthenticatedUser user,
                                        @Valid @RequestBody DeviceTokenRequest request) {
        deviceTokenService.deactivate(user.userSeq(), request);
        return ApiResponse.success();
    }
}
