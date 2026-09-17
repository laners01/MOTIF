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

    /**
     * 내 패스 일람 (API 명세 §11.9) — <b>전량</b>, 만료 늦은 순.
     *
     * <p>{@link #findActive} 와 나누는 이유는 <b>대상이 다르기 때문</b>이다. 그쪽은 어드민
     * §3.14.2 의 "지금 쓸 수 있는 패스" 라 유효한 것만 세 칸으로 투영하고, 여기는 결제
     * 이력 성격이라 <b>만료·환불된 것까지 전체 칸</b>이다. 상태로 거르지 않는다.
     *
     * <p><b>기간이 지난 {@code ACTIVE} 행도 그대로 준다.</b> 만료 배치가 아직 없어 정상적
     * 으로 존재하는 상태이고, 서버가 여기서 {@code EXPIRED} 로 바꿔 주면 DB 와 응답이
     * 갈린다 — 이용 가능 판정은 이 API 의 일이 아니다(§11.4·§4.7 이 답한다).
     *
     * <p>정렬이 {@code expires_dttm} 내림차순인 것은 유효한 패스가 자연히 위로 오기
     * 때문이다(만료가 가장 늦다). 동률은 {@code pass_seq} 로 끊는다 — 같은 초에 두 건이
     * 생기면 순서가 매번 달라진다.
     *
     * <p>아티스트 이름을 위해 조인하지 않는다. 호출부가 {@code ArtistRepository.findCards}
     * 로 한 번에 받는다 — 패스를 읽는 다른 경로까지 조인이 따라붙지 않게({@link #findActive}
     * 와 같은 규칙).
     */
    List<MotifPass> findByUserSeqOrderByExpiresDttmDescPassSeqDesc(Integer userSeq);
}
