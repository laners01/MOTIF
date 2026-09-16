package com.ssafy.motif.coreapi.domain.notification;

import com.ssafy.motif.coreapi.domain.notification.dto.DeviceTokenRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * §14.1·§14.2 를 실제 DB에 대고 검증한다 (S15P21D104-615).
 *
 * <p><b>이 테스트의 값은 교차 스키마 권한에 있다.</b> core-api 는 로컬에서도
 * {@code core_app} 으로 붙으므로, {@code motif_notif.device_tokens} 에 쓰는 순간
 * changeSet 0002 의 GRANT 3종(스키마 USAGE · 테이블 · <b>IDENTITY 시퀀스</b>)이 전부
 * 걸린다. 시퀀스 권한만 빠져도 INSERT 가 {@code permission denied for sequence} 로
 * 떨어지는데, 테이블 권한만 보고 있으면 원인을 못 찾는다.
 *
 * <p>나머지 하나는 재할당이 <b>원자적</b>인가다. {@code ON CONFLICT} 대신 조회 후
 * 분기로 짜면 두 기기가 동시에 같은 토큰을 보낼 때 UNIQUE 위반(500)이 난다.
 *
 * <p>실행 전 {@code backend/local-infra && docker compose up -d --wait} 와
 * <b>app-notification 을 한 번 기동</b>해 {@code motif_notif} 마이그레이션을 적용해야 한다
 * — 그 테이블과 GRANT 를 만드는 것이 그 앱의 Liquibase 뿐이다.
 */
@Tag("local-db")
@SpringBootTest
@Transactional
class DeviceTokenServiceLocalDbTest {

    private static final int USER_A = 90_001;
    private static final int USER_B = 90_002;

    @Autowired
    private DeviceTokenService deviceTokenService;
    @Autowired
    private JdbcTemplate jdbc;

    private String token;

    @BeforeEach
    void setUp() {
        // UNIQUE(fcm_token) 이라 테스트끼리 부딪히지 않게 매번 새 값을 쓴다
        token = "tok-" + UUID.randomUUID();
    }

    @Test
    @DisplayName("등록 — 새 토큰이 활성으로 들어간다 (시퀀스 권한까지 걸린다)")
    void register() {
        deviceTokenService.register(USER_A, new DeviceTokenRequest(token, "ANDROID"));

        Map<String, Object> row = row(token);
        assertThat(row.get("user_seq")).isEqualTo(USER_A);
        assertThat(row.get("platform")).isEqualTo("ANDROID");
        assertThat(row.get("is_active")).isEqualTo(true);
        assertThat(row.get("last_used_dttm")).isNotNull();
    }

    @Test
    @DisplayName("등록 — platform 을 생략하면 ANDROID")
    void registerDefaultsPlatform() {
        deviceTokenService.register(USER_A, new DeviceTokenRequest(token, null));

        assertThat(row(token).get("platform")).isEqualTo("ANDROID");
    }

    @Test
    @DisplayName("재할당 — 같은 기기가 다른 계정으로 오면 행을 새로 만들지 않고 소유자를 옮긴다")
    void reassign() {
        deviceTokenService.register(USER_A, new DeviceTokenRequest(token, "ANDROID"));
        deviceTokenService.register(USER_B, new DeviceTokenRequest(token, "IOS"));

        // 새 행을 만들면 옛 계정의 알림이 남의 기기로 계속 간다
        assertThat(count(token)).isEqualTo(1);
        Map<String, Object> row = row(token);
        assertThat(row.get("user_seq")).isEqualTo(USER_B);
        assertThat(row.get("platform")).isEqualTo("IOS");
        assertThat(row.get("is_active")).isEqualTo(true);
    }

    @Test
    @DisplayName("재등록 — 로그아웃으로 내려간 토큰이 다시 켜진다")
    void reactivate() {
        deviceTokenService.register(USER_A, new DeviceTokenRequest(token, "ANDROID"));
        deviceTokenService.deactivate(USER_A, new DeviceTokenRequest(token, null));
        assertThat(row(token).get("is_active")).isEqualTo(false);

        deviceTokenService.register(USER_A, new DeviceTokenRequest(token, "ANDROID"));

        assertThat(row(token).get("is_active")).isEqualTo(true);
    }

    @Test
    @DisplayName("해제 — 행을 지우지 않고 is_active 만 내린다")
    void deactivate() {
        deviceTokenService.register(USER_A, new DeviceTokenRequest(token, "ANDROID"));

        deviceTokenService.deactivate(USER_A, new DeviceTokenRequest(token, null));

        assertThat(count(token)).isEqualTo(1);
        assertThat(row(token).get("is_active")).isEqualTo(false);
    }

    @Test
    @DisplayName("해제 — 미등록 토큰도 조용히 성공한다 (§14.2 멱등)")
    void deactivateIsIdempotent() {
        assertThatCode(() -> deviceTokenService
                .deactivate(USER_A, new DeviceTokenRequest("tok-없는것", null)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("해제 — 남의 토큰은 내리지 못한다")
    void cannotDeactivateOthersToken() {
        deviceTokenService.register(USER_A, new DeviceTokenRequest(token, "ANDROID"));

        // 토큰 문자열만 알면 남의 기기 알림을 끌 수 있으면 안 된다
        deviceTokenService.deactivate(USER_B, new DeviceTokenRequest(token, null));

        assertThat(row(token).get("is_active")).isEqualTo(true);
    }

    private Map<String, Object> row(String fcmToken) {
        return jdbc.queryForMap("""
                SELECT user_seq, platform, is_active, last_used_dttm
                  FROM motif_notif.device_tokens
                 WHERE fcm_token = ?""", fcmToken);
    }

    private int count(String fcmToken) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM motif_notif.device_tokens WHERE fcm_token = ?",
                Integer.class, fcmToken);
        return count == null ? 0 : count;
    }
}
