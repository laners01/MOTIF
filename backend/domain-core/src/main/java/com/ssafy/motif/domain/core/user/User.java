package com.ssafy.motif.domain.core.user;

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

/**
 * 모든 서비스 행위 주체 (DB SPEC v0.15 §1, changeSet 0002-01).
 *
 * <p>{@code repMotifResultSeq}는 순환 참조 방지로 DDL에 FK가 없다 — 논리 참조이므로
 * 연관이 아니라 값으로 둔다. {@code modUsrSeq}도 같은 이유로 값이다(자기참조 FK).
 */
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_seq")
    private Integer userSeq;

    @Column(name = "nickname", nullable = false, length = 50)
    private String nickname;

    @Column(name = "profile_image_key", length = 255)
    private String profileImageKey;

    @Column(name = "rep_motif_result_seq")
    private Long repMotifResultSeq;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 10)
    private UserRole role;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted;

    @Column(name = "mod_usr_seq")
    private Integer modUsrSeq;

    private User(String nickname, UserRole role) {
        this.nickname = nickname;
        this.role = role;
        this.deleted = false;
    }

    /**
     * 가입 시점 생성. 닉네임은 {@code user_{seq}}로 확정해야 하지만 seq는 INSERT
     * 이후에만 알 수 있어, 여기서는 자리표시자를 넣고
     * {@link #applyInitialNickname()}으로 교체한다 (명세 §2.1 기본안).
     */
    public static User signUp() {
        return new User(NICKNAME_PLACEHOLDER, UserRole.USER);
    }

    /** INSERT 직후 호출 — {@code user_{seq}} 임시 닉네임을 확정한다. */
    public void applyInitialNickname() {
        this.nickname = "user_" + this.userSeq;
    }

    /**
     * 자리표시자. 닉네임에 UNIQUE가 없어(0012는 GIN trigram만) 동시 가입에도
     * 충돌하지 않는다. 길이는 CHECK(1~50) 범위 안이다.
     */
    private static final String NICKNAME_PLACEHOLDER = "user";
}
