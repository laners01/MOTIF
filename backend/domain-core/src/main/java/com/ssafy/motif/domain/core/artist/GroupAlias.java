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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 그룹 별칭 (DB SPEC §42, changeSet 0018-02 — ADR-0014).
 *
 * <p>구조·규칙은 {@link ArtistAlias} 와 같고 대상만 다르다 — 거기 적은 것(찾기 전용,
 * {@code language_code} 는 검색에 안 씀, UNIQUE 없음, 소프트 삭제 연쇄 없음)이 그대로 걸린다.
 *
 * <p><b>{@link ArtistAlias} 와 한 엔티티로 합치지 않는다.</b> FK 대상이 다른 것도 있지만,
 * 결정적인 이유는 검색이 <b>"이 매치가 그룹 매치였는가" 를 알아야 한다</b>는 것이다 —
 * API §5.2 는 그룹 별칭에 걸리면 그 그룹의 비삭제 멤버 전원을 결과에 펼치고, 그렇게 들어온
 * 멤버를 본인 매치보다 뒤로 정렬한다. 테이블·엔티티가 갈려 있으면 그 판정이 조회 구조에서
 * 공짜로 나온다. 하나로 합쳐 {@code targetType} 같은 칸을 두면 같은 사실을 두 번 표현하게
 * 되고 FK 도 못 건다(ADR-0014 D2).
 */
@Entity
@Table(name = "group_aliases")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GroupAlias extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "alias_seq")
    private Integer aliasSeq;

    @Column(name = "group_seq", nullable = false, updatable = false)
    private Integer groupSeq;

    /** ISO 639-1 2자 소문자. {@code CHAR} 매핑 근거는 {@link ArtistAlias#getLanguageCode()}. */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "language_code", nullable = false, length = 2)
    private String languageCode;

    @Column(name = "alias", nullable = false, length = 100)
    private String alias;

    @Column(name = "reg_usr_seq", nullable = false, updatable = false)
    private Integer regUsrSeq;

    /** 어드민 등록 (어드민 §3.4.6). 대상 그룹 비삭제 확인은 서비스가 먼저 끝내고 부른다. */
    public static GroupAlias register(Integer groupSeq, String languageCode, String alias,
                                      Integer regUsrSeq) {
        GroupAlias entity = new GroupAlias();
        entity.groupSeq = groupSeq;
        entity.languageCode = languageCode;
        entity.alias = alias;
        entity.regUsrSeq = regUsrSeq;
        return entity;
    }

    /** 수정 (어드민 §3.4.7) — 전체 치환. 근거는 {@link ArtistAlias#change}. */
    public void change(String languageCode, String alias) {
        this.languageCode = languageCode;
        this.alias = alias;
    }
}
