package com.ssafy.motif.domain.core.admin;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * 백오피스 로그인 주체 (changeSet 0002-04 + 0014, 어드민 명세 §1.2).
 *
 * <p><b>가입 경로가 없다.</b> 계정은 시드 changeSet 으로만 생긴다(부록 B) — 그래서
 * 생성자도 정적 팩터리도 두지 않는다. 여기서 만들 수 있게 열어 두면 "어드민을 코드로
 * 만들 수 있다"는 오해가 생기고, 그 순간 BCrypt 해시를 어디서 넣을지부터 흔들린다.
 *
 * <p>{@code userSeq} 는 {@code users(role='ADMIN')} 를 가리킨다. 감사 <b>컬럼</b>
 * ({@code reg_usr_seq} 등)에 들어가는 값이 이것이고, 감사 <b>로그</b>의 수행자는
 * {@code adminSeq} 다 — 둘은 다른 값이라 토큰이 함께 싣는다(§1.3,
 * {@code AuthenticatedAdmin} javadoc).
 *
 * <p>{@code regDttm} 은 매핑하지 않는다 — DB 기본값으로 채워지고 읽을 곳이 없다.
 * 매핑하지 않은 컬럼은 {@code ddl-auto: validate} 대상이 아니다.
 */
@Entity
@Table(name = "admins")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Admin {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "admin_seq")
    private Integer adminSeq;

    @Column(name = "login_id", nullable = false, length = 30)
    private String loginId;

    /** BCrypt 해시 60자 고정. 평문은 어디에도 저장하지 않는다(부록 B). */
    @Column(name = "password_hash", nullable = false, length = 60)
    private String passwordHash;

    @Column(name = "name", nullable = false, length = 30)
    private String name;

    /** {@code FALSE} 면 로그인을 거부한다 — 행은 남긴다(감사 참조, 부록 B 4항). */
    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "user_seq", nullable = false)
    private Integer userSeq;

    @Column(name = "last_login_dttm")
    private OffsetDateTime lastLoginDttm;

    /**
     * 로그인 성공 시각 기록 (§3.1.1 "last_login_dttm 갱신").
     *
     * <p>실패는 남기지 않는다 — 실패 잠금(5회)이 §8 미결이라 실패 횟수를 셀 자리가
     * 아직 없고, 여기에 억지로 끼우면 잠금 설계가 정해질 때 되돌려야 한다.
     */
    public void markLoggedIn(OffsetDateTime at) {
        this.lastLoginDttm = at;
    }
}
