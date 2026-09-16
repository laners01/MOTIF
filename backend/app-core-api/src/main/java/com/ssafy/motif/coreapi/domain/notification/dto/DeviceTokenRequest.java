package com.ssafy.motif.coreapi.domain.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * §14.1 등록 · §14.2 해제 공용 요청.
 *
 * <p>{@code platform} 은 해제에서 쓰지 않는다 — 토큰 하나로 행이 특정된다.
 * 별도 DTO 를 두면 같은 필드 검증이 두 벌이 된다.
 *
 * @param platform {@code ck_device_tokens_platform} 과 같은 값. enum 을 새로 만들지 않는다 —
 *                 {@code domain-notif} 의 {@code Platform} 을 가져오면 모듈 경계가 무너지고,
 *                 여기서 또 정의하면 CHECK 제약과 두 벌이 된다. 검증만 하고 문자열로 넘긴다
 */
public record DeviceTokenRequest(
        @NotBlank(message = "fcmToken 은 필수다")
        String fcmToken,

        @Pattern(regexp = "ANDROID|IOS|WEB", message = "platform 은 ANDROID·IOS·WEB 중 하나다")
        String platform) {

    private static final String DEFAULT_PLATFORM = "ANDROID";

    /** 생략하면 ANDROID — 현재 앱이 안드로이드뿐이다(DB 기본값과 같다). */
    public String platformOrDefault() {
        return platform == null || platform.isBlank() ? DEFAULT_PLATFORM : platform;
    }
}
