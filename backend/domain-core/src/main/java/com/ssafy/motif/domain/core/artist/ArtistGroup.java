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
 * 아티스트 그룹 (DB SPEC v0.15 §8, changeSet 0001-04).
 *
 * <p><b>그룹이 소속사를 소유한다.</b> 그룹 소속 아티스트의 소속사는 그룹을 통해서만
 * 바뀐다(DB §9) — 그래서 아티스트 등록·수정은 {@code companySeq} 가 이 행의
 * {@code companySeq} 와 같은지 대조한다(어드민 명세 §3.5.2).
 *
 * <p>그 불변식 때문에 {@link #changeCompany} 는 <b>혼자 쓰면 안 된다</b> — 소속사를 옮기면
 * 비삭제 멤버 전원의 {@code artists.company_seq} 를 같은 트랜잭션에서 따라 옮겨야 한다
 * (§3.4.3, DB §8). 그 짝은 {@code AdminGroupService.update} 가 맞춘다.
 *
 * <p>그룹명은 중복을 허용한다(DB §8) — 소속사와 달리 UNIQUE 가 없다.
 */
@Entity
@Table(name = "artist_groups")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ArtistGroup extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "group_seq")
    private Integer groupSeq;

    @Column(name = "company_seq", nullable = false)
    private Integer companySeq;

    @Column(name = "group_name", nullable = false, length = 50)
    private String groupName;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted;

    @Column(name = "reg_usr_seq", nullable = false, updatable = false)
    private Integer regUsrSeq;

    @Column(name = "mod_usr_seq")
    private Integer modUsrSeq;

    /** 어드민 등록 (§3.4.2). 소속사 비삭제 확인은 서비스가 먼저 끝내고 부른다. */
    public static ArtistGroup register(Integer companySeq, String groupName, Integer regUsrSeq) {
        ArtistGroup group = new ArtistGroup();
        group.companySeq = companySeq;
        group.groupName = groupName;
        group.regUsrSeq = regUsrSeq;
        return group;
    }

    /** 그룹명 수정 (§3.4.3). {@code null} 은 "그대로" 라는 뜻이다. */
    public void rename(String groupName, Integer modUsrSeq) {
        if (groupName != null) {
            this.groupName = groupName;
            this.modUsrSeq = modUsrSeq;
        }
    }

    /**
     * 소속사 이동 (§3.4.3).
     *
     * <p><b>멤버 갱신과 짝이다.</b> 호출부가 같은 트랜잭션에서
     * {@link ArtistRepository#moveMembersCompany} 를 반드시 함께 부른다 — 이것만 부르면
     * 그룹과 멤버의 소속사가 어긋나 §3.5 의 불변식이 깨진다.
     */
    public void changeCompany(Integer companySeq, Integer modUsrSeq) {
        this.companySeq = companySeq;
        this.modUsrSeq = modUsrSeq;
    }

    /** 소프트 삭제 (§3.4.4). 멤버 검사는 서비스가 먼저 끝내고 부른다. */
    public void softDelete(Integer modUsrSeq) {
        this.deleted = true;
        this.modUsrSeq = modUsrSeq;
    }
}
