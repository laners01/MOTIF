package com.ssafy.motif.domain.core.artist;

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
 * <p><b>부분 매핑이다.</b> 그룹 CRUD(§3.4)가 필요로 하는 감사 칼럼은 그 티켓에서 더한다.
 */
@Entity
@Table(name = "artist_groups")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ArtistGroup {

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
}
