package com.ssafy.motif.domain.notif.token;

/**
 * {@code ck_device_tokens_platform} 과 같은 값이다 (changeSet 0001-01).
 *
 * <p>현재 앱은 안드로이드뿐이지만 CHECK 제약이 세 값을 허용하므로 enum 도 맞춰 둔다 —
 * 좁게 잡으면 DB 가 허용한 값에 애플리케이션이 터진다.
 */
public enum Platform {
    ANDROID, IOS, WEB
}
