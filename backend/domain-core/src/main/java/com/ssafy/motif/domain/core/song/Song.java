package com.ssafy.motif.domain.core.song;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 곡 (DB SPEC v0.15 §11, changeSet 0003-04).
 *
 * <p><b>등록만 있고 수정·삭제가 없다 — 곡은 불변이다</b>(어드민 명세 ADMIN-05, 회의 확정 R1).
 * 오타·정보 오류는 고치는 게 아니라 <b>새 곡을 등록하고 챌린지의 곡을 재연결</b>한다(§3.7.4).
 * 이미 등록된 곡을 고치면 그 곡을 쓰는 다른 챌린지까지 같이 바뀌기 때문이다.
 * 참조가 없어진 곡 행은 그대로 남는다 — 운영상 무해하다.
 *
 * <p>그래서 이 엔티티에는 <b>수정자가 없다</b>. {@code songs} 에 {@code mod_dttm}·
 * {@code is_deleted} 컬럼 자체가 없고, 컬럼을 추가하지 않기로 확정됐다(R1).
 * {@link com.ssafy.motif.common.jpa.BaseTimeEntity} 를 상속하지 않는 것도 같은 이유다
 * ({@code Subscription} 과 같은 예외) — 상속하면 없는 {@code mod_dttm} 을 매핑하게 된다.
 *
 * <p>중복을 막지 않는다(UNIQUE 없음) — 리믹스·재녹음이 같은 제목을 갖는다. 화면이 등록 전
 * 동일 곡명·가수명 존재를 경고만 한다(§3.6.2).
 */
@Entity
@Table(name = "songs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Song {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "song_seq")
    private Integer songSeq;

    @Column(name = "title", nullable = false, updatable = false)
    private String title;

    @Column(name = "singer_name", nullable = false, updatable = false)
    private String singerName;

    /** 감사 주체 — 어드민 JWT 의 {@code usr}({@code admins.user_seq}) 다(§1.3). {@code adminSeq} 가 아니다. */
    @Column(name = "reg_usr_seq", nullable = false, updatable = false)
    private Integer regUsrSeq;

    @Column(name = "reg_dttm", nullable = false, updatable = false)
    private Instant regDttm;

    private Song(String title, String singerName, Integer regUsrSeq) {
        this.title = title;
        this.singerName = singerName;
        this.regUsrSeq = regUsrSeq;
    }

    /** 어드민 등록 (§3.6.2) — 유일한 쓰기 경로다. */
    public static Song register(String title, String singerName, Integer regUsrSeq) {
        return new Song(title, singerName, regUsrSeq);
    }

    @PrePersist
    protected void onPrePersist() {
        this.regDttm = Instant.now();
    }
}
