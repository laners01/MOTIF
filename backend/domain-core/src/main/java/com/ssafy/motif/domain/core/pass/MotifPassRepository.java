package com.ssafy.motif.domain.core.pass;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MotifPassRepository extends JpaRepository<MotifPass, Integer> {

    /**
     * 이 회원의 유효 패스 — 만료 임박순 (어드민 명세 §3.14.2 {@code activePasses}).
     *
     * <p><b>{@code current_timestamp} 는 DB 가 평가한다.</b> 만료 시각을 자바로 꺼내 와
     * 비교하면 앱 서버 시계가 DB 와 어긋난 만큼 만료 직전·직후 판정이 갈린다
     * ({@code MotifPassPriceRepository.findActivePassExpiry} 와 같은 근거). 파라미터로
     * 시각을 받지 않는 것도 그래서다 — 호출부마다 다른 시계를 넘길 여지를 없앤다.
     *
     * <p>아티스트 이름을 얻으려 {@code @ManyToOne} 을 달지 않는다. 패스를 읽는 다른
     * 경로(§3.9 경고 카운트 등)까지 조인이 따라붙는다 — 필요한 칸만 ON 조인으로 꺼낸다
     * ({@code ArtistListRow} 와 같은 규칙).
     *
     * <p><b>건수는 이 목록의 크기다.</b> {@code uq_motif_passes_user_artist} 로 아티스트당
     * 한 행이라 회원 하나의 유효 패스는 많아야 아티스트 수만큼이다. 카운트 쿼리를 따로
     * 두면 "패스 2개" 인데 목록은 1줄인 화면이 나올 수 있다 — 같은 판정을 두 번 쓰지 않는다.
     */
    @Query("""
            select new com.ssafy.motif.domain.core.pass.ActivePassRow(
                       p.artistSeq, u.nickname, p.expiresDttm)
            from MotifPass p
              join Artist a on a.artistSeq = p.artistSeq
              join User u on u.userSeq = a.userSeq
            where p.userSeq = :userSeq
              and p.status = com.ssafy.motif.domain.core.pass.PassStatus.ACTIVE
              and p.expiresDttm > current_timestamp
            order by p.expiresDttm asc
            """)
    List<ActivePassRow> findActive(@Param("userSeq") Integer userSeq);
}
