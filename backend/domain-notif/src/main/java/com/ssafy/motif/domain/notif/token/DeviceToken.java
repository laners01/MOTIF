package com.ssafy.motif.domain.notif.token;

import com.ssafy.motif.common.jpa.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * FCM 디바이스 토큰 (DB SPEC v0.15 §38, changeSet 0001-01) — {@code motif_notif} 의 유일한 테이블.
 *
 * <p><b>FCM 은 사용자가 아니라 기기로 간다.</b> 한 사용자가 여러 토큰을 갖는다 —
 * 폰·태블릿, 재설치, 앱 데이터 삭제가 전부 새 토큰을 만든다. 발송은 항상
 * {@code user_seq → 활성 토큰 N개} 로 펼친 뒤 한다.
 *
 * <p><b>{@code fcm_token} 이 UNIQUE 다.</b> 같은 기기가 다른 계정으로 로그인하면 행을
 * 새로 만드는 것이 아니라 <b>소유자를 옮긴다</b>(§14.1 재할당). 새로 만들면 옛 계정의
 * 알림이 남의 기기로 간다.
 *
 * <p><b>{@code user_seq} 는 {@code motif_core.users} 의 논리 참조다.</b> 교차 스키마 FK 를
 * 걸지 않는다 — 스키마가 갈려 있고 계정도 다르다(ADR-0004). 정합은 애플리케이션이 진다.
 *
 * <p>비활성으로 내려가는 경로가 셋이다 — 로그아웃(§14.2), 회원 정지(S15P21D104-583),
 * <b>FCM 이 {@code UNREGISTERED}·{@code INVALID_ARGUMENT} 를 돌려줬을 때</b>. 마지막 것을
 * 빼먹으면 죽은 토큰에 영원히 쏘고 발송 지표가 계속 더러워진다.
 */
@Entity
@Table(name = "device_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeviceToken extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "token_seq")
    private Integer tokenSeq;

    @Column(name = "user_seq", nullable = false)
    private Integer userSeq;

    @Column(name = "fcm_token", nullable = false)
    private String fcmToken;

    @Enumerated(EnumType.STRING)
    @Column(name = "platform", nullable = false, length = 10)
    private Platform platform;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "last_used_dttm")
    private Instant lastUsedDttm;

    public static DeviceToken register(Integer userSeq, String fcmToken, Platform platform) {
        DeviceToken token = new DeviceToken();
        token.userSeq = userSeq;
        token.fcmToken = fcmToken;
        token.platform = platform;
        token.active = true;
        return token;
    }

    /**
     * 재할당 — 같은 토큰이 다른 계정으로 왔다 (§14.1).
     *
     * <p>비활성이었더라도 다시 켠다. 로그아웃으로 내려간 기기에 같은 사람이 다시
     * 로그인하는 흐름이 가장 흔하다.
     */
    public void reassignTo(Integer userSeq, Platform platform) {
        this.userSeq = userSeq;
        this.platform = platform;
        this.active = true;
        this.lastUsedDttm = Instant.now();
    }

    /** 로그아웃(§14.2) · 회원 정지(583) · FCM 이 죽은 토큰이라고 답했을 때. */
    public void deactivate() {
        this.active = false;
    }
}
