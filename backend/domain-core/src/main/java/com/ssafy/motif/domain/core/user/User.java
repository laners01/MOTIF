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

    /**
     * 어드민이 만드는 아티스트 유저 (어드민 명세 §3.5.2).
     *
     * <p>{@link #signUp()} 과 달리 닉네임이 처음부터 확정이다 — 가입 흐름이 아니라
     * 운영자가 이름을 적어 넣는 등록이라 {@code user_{seq}} 자리표시자 단계가 없다.
     * {@code user_accounts} 는 만들지 않는다 — 아티스트는 로그인하지 않는다(회의 확정).
     */
    public static User createArtist(String nickname) {
        return new User(nickname, UserRole.ARTIST);
    }

    /**
     * 프로필 이미지 키 교체 — {@code profiles/{uuid}.jpg} (어드민 명세 §1.4).
     *
     * <p>바이트는 presign 으로 브라우저 → S3 직행했고 여기 오는 것은 <b>키뿐</b>이다.
     * {@code null} 은 "그 필드는 그대로" 라는 뜻이라 지우기가 아니다 — 이미지 제거가
     * 필요해지면 빈 문자열 같은 표식이 아니라 전용 경로를 만든다.
     */
    public void changeProfileImageKey(String profileImageKey) {
        if (profileImageKey != null) {
            this.profileImageKey = profileImageKey;
        }
    }

    /** 수정 주체 기록 — {@code users.mod_usr_seq} (어드민 명세 §3.5.4 "name 변경은 … mod_usr_seq"). */
    public void recordModifier(Integer modUsrSeq) {
        this.modUsrSeq = modUsrSeq;
    }

    /**
     * 대표 모티프 지정·해제 (API 명세 §11.8).
     *
     * <p><b>여기가 대표 단일성이 지켜지는 자리다.</b> 칸이 하나뿐이라 새 값을 덮어쓰면
     * 이전 대표는 그대로 해제된다 — "이전 대표를 찾아 끄는" 코드를 두지 않는다. 그렇게
     * 짜면 조회와 해제 사이에 다른 요청이 대표를 바꿨을 때 남의 지정을 되돌린다.
     *
     * <p>{@code null} 이 해제다. {@link #getRepMotifResultSeq()} 가 가리키는 결과물이
     * 삭제될 때도 이 메서드로 비운다 — FK 가 없는 논리 참조라 DB 가 정리해 주지 않는다.
     */
    public void changeRepMotif(Long repMotifResultSeq) {
        this.repMotifResultSeq = repMotifResultSeq;
    }

    /**
     * 소프트 삭제 — 아티스트 삭제 연쇄 (어드민 명세 §3.5.5).
     *
     * <p>{@code artists} 와 {@code users} 는 한 쌍이라({@code uq_artists_user}) 둘 중
     * 하나만 지우면 이름 없는 아티스트이거나 아무 역할 없는 ARTIST 유저가 남는다 —
     * 등록(§3.5.2)이 한 트랜잭션인 것과 같은 이유로 삭제도 한 트랜잭션이다.
     */
    public void softDelete(Integer modUsrSeq) {
        this.deleted = true;
        this.modUsrSeq = modUsrSeq;
    }

    /** INSERT 직후 호출 — {@code user_{seq}} 임시 닉네임을 확정한다. */
    public void applyInitialNickname() {
        this.nickname = "user_" + this.userSeq;
    }

    /**
     * 닉네임 변경 (명세 §3.3 온보딩 확정, §4.2 수정).
     *
     * <p>형식 검증은 요청 DTO 가 한다 — 엔티티는 DB CHECK(1~50자)만 지키면 되고,
     * API 규칙(2~20자·허용 문자)은 계층이 다르다. 중복 검사는 하지 않는다(C1 회의 확정).
     */
    public void changeNickname(String nickname) {
        this.nickname = nickname;
    }

    /**
     * 자리표시자. 닉네임에 UNIQUE가 없어(0012는 GIN trigram만) 동시 가입에도
     * 충돌하지 않는다. 길이는 CHECK(1~50) 범위 안이다.
     */
    private static final String NICKNAME_PLACEHOLDER = "user";
}
