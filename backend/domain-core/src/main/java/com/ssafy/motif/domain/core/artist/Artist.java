package com.ssafy.motif.domain.core.artist;

import com.ssafy.motif.common.jpa.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 아티스트 (DB SPEC v0.15 §9, changeSet 0003-01).
 *
 * <p><b>표시 이름이 이 테이블에 없다.</b> {@code artists} 는 {@code users.role='ARTIST'}
 * 사용자의 도메인 확장이라({@code uq_artists_user}) 이름은 {@code users.nickname} 이다.
 * 등록도 두 행을 <b>한 트랜잭션</b>에 만든다(어드민 명세 §3.5.2) — {@code users} 없이
 * {@code artists} 만 있는 상태는 {@code uq_artists_user}·FK 로 애초에 만들 수 없다.
 *
 * <p><b>{@code group_seq} 와 {@code company_seq} 는 함께 움직인다.</b> 그룹에 속한
 * 아티스트의 소속사는 그룹이 정하므로, 둘을 따로 바꾸는 setter 를 두지 않고
 * {@link #changeOrganization} 하나로만 바꾼다 — 소속사만 슬쩍 바뀌는 경로를 코드에서
 * 없앤다(DB §9, §3.5.4 {@code ARTIST_COMPANY_LOCKED}).
 *
 * <p>{@code subscriberCount} 는 거울 카운터다. 구독 생성·해제와 <b>같은 트랜잭션</b>에서
 * 원자적 UPDATE 로 증감한다 — 읽고-더하고-쓰면 동시 구독에서 증가분이 유실된다
 * ({@link ArtistRepository#increaseSubscriberCount}).
 *
 * <p>{@code mod_usr_seq} 칼럼은 {@code artists} 에 <b>없다</b>(DDL 0003-01) — 수정 주체는
 * {@code users.mod_usr_seq} 와 감사 로그에 남는다.
 */
@Entity
@Table(name = "artists")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Artist extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "artist_seq")
    private Integer artistSeq;

    @Column(name = "user_seq", nullable = false, updatable = false)
    private Integer userSeq;

    /** {@code null} = 솔로. 그룹에 속하면 {@link #companySeq} 는 그룹 소속사와 같다. */
    @Column(name = "group_seq")
    private Integer groupSeq;

    @Column(name = "company_seq", nullable = false)
    private Integer companySeq;

    @Column(name = "motif_bg_image_key", length = 255)
    private String motifBgImageKey;

    @Column(name = "bio")
    private String bio;

    @Column(name = "subscriber_count", nullable = false)
    private int subscriberCount;

    @Column(name = "reg_usr_seq", nullable = false, updatable = false)
    private Integer regUsrSeq;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted;

    /** 어드민 등록 (§3.5.2). 그룹·소속사 정합은 서비스가 이미 맞춰 놓고 부른다. */
    public static Artist register(Integer userSeq, Integer groupSeq, Integer companySeq,
                                  String bio, String motifBgImageKey, Integer regUsrSeq) {
        Artist artist = new Artist();
        artist.userSeq = userSeq;
        artist.groupSeq = groupSeq;
        artist.companySeq = companySeq;
        artist.bio = bio;
        artist.motifBgImageKey = motifBgImageKey;
        artist.regUsrSeq = regUsrSeq;
        return artist;
    }

    /**
     * 그룹·소속사 동시 변경 (§3.5.4).
     *
     * @param groupSeq {@code null} 이면 솔로 전환 — 그때 소속사는 {@code companySeq} 가 정한다
     */
    public void changeOrganization(Integer groupSeq, Integer companySeq) {
        this.groupSeq = groupSeq;
        this.companySeq = companySeq;
    }

    /**
     * 소프트 삭제 (§3.5.5).
     *
     * <p>수정 주체를 받지 않는다 — {@code artists} 에 {@code mod_usr_seq} 칼럼이 없다
     * (DDL 0003-01, 클래스 javadoc). 누가 지웠는지는 {@code users.mod_usr_seq} 와
     * 감사 로그에 남는다. 에셋·가격 연쇄는 서비스가 같은 트랜잭션에서 이어 붙인다.
     */
    public void softDelete() {
        this.deleted = true;
    }

    /** {@code null} 은 "그 필드는 그대로" 라는 뜻이다 (§3.5.4 부분 수정). */
    public void changeProfile(String bio, String motifBgImageKey) {
        if (bio != null) {
            this.bio = bio;
        }
        if (motifBgImageKey != null) {
            this.motifBgImageKey = motifBgImageKey;
        }
    }
}
