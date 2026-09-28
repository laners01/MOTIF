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
 * 아티스트 별칭 (DB SPEC §41, changeSet 0018-01 — ADR-0014).
 *
 * <p><b>찾기 전용이다 — 보여주기용이 아니다.</b> 팬 앱 검색(API §5.2)이 {@code users.nickname}
 * 대신 이 {@code alias} 를 훑지만, 응답에 실리는 이름은 여전히 {@code users.nickname} 이다.
 * 별칭을 표시에 쓰면 같은 아티스트가 질의에 따라 다른 이름으로 보이고, 공통 아티스트 요약을
 * 쓰는 15개 엔드포인트가 전부 흔들린다(상세 명세 README §3.5).
 *
 * <p><b>{@code language_code} 는 검색에 쓰지 않는다.</b> 한국어 UI 사용자도 "aespa" 를 영문으로
 * 치는 일이 흔해서, 요청 언어로 거르면 오히려 안 나온다 — 어드민이 언어별로 정리해 넣기 위한
 * 저장 구분일 뿐이다.
 *
 * <p><b>UNIQUE 가 없다.</b> 한 언어에 별칭 여러 개가 정상이고({@code aespa}·{@code æspa},
 * 활동명·본명) 같은 값이 두 번 들어가도 막지 않는다. 검색이 {@code artistSeq} 로 중복을
 * 제거하므로 결과가 겹치지 않는다.
 *
 * <p><b>소프트 삭제 칼럼이 없다.</b> 아티스트를 소프트 삭제해도 이 행은 그대로 두고 조회가
 * 조인에서 {@code artists.is_deleted = false} 로 거른다 — 복구하면 별칭이 살아 돌아온다.
 * 어드민이 직접 지우는 것(어드민 §3.5.9)만 하드 삭제이며, 그건 별개 동작이다.
 *
 * <p>{@code artistSeq} 를 {@code @ManyToOne} 으로 매핑하지 않는다 — {@link Artist} 와 같은
 * 근거다. 연관을 걸면 별칭을 읽을 때마다 조인이 따라붙는다.
 *
 * <p>{@code mod_usr_seq} 칼럼이 없다({@link Artist} 와 같은 이유) — 수정 주체는 감사 로그에
 * 남는다.
 */
@Entity
@Table(name = "artist_aliases")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ArtistAlias extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "alias_seq")
    private Integer aliasSeq;

    @Column(name = "artist_seq", nullable = false, updatable = false)
    private Integer artistSeq;

    /**
     * ISO 639-1 2자 소문자.
     *
     * <p>DDL 이 {@code CHAR} 라 JDBC 타입을 맞춘다 — 기본 매핑은 VARCHAR 라
     * {@code ddl-auto: validate} 가 막는다({@code Order.countryCode} 와 같다).
     */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "language_code", nullable = false, length = 2)
    private String languageCode;

    @Column(name = "alias", nullable = false, length = 100)
    private String alias;

    @Column(name = "reg_usr_seq", nullable = false, updatable = false)
    private Integer regUsrSeq;

    /** 어드민 등록 (어드민 §3.5.7). 대상 아티스트 비삭제 확인은 서비스가 먼저 끝내고 부른다. */
    public static ArtistAlias register(Integer artistSeq, String languageCode, String alias,
                                       Integer regUsrSeq) {
        ArtistAlias entity = new ArtistAlias();
        entity.artistSeq = artistSeq;
        entity.languageCode = languageCode;
        entity.alias = alias;
        entity.regUsrSeq = regUsrSeq;
        return entity;
    }

    /**
     * 수정 (어드민 §3.5.8) — 부분 수정이 아니라 전체 치환이다.
     *
     * <p>{@code null} 을 "그대로" 로 해석하지 않는다. 칸이 둘뿐이고 어드민 화면이 둘 다
     * 채워 보내므로, 부분 수정 규약을 두면 빈 값을 보낸 것과 구분이 안 된다.
     */
    public void change(String languageCode, String alias) {
        this.languageCode = languageCode;
        this.alias = alias;
    }
}
