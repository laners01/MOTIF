package com.ssafy.motif.domain.core.pass;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface MotifPassPriceRepository extends JpaRepository<MotifPassPrice, Integer> {

    /** §3.9.1 — 아티스트 가격표(전 국가·기간). 화면이 국가×개월 그리드로 펼친다. */
    List<MotifPassPrice> findByArtistSeqOrderByCountrySeqAscDurationMonthsAsc(Integer artistSeq);

    /** {@code uq_motif_pass_prices_artist_country_duration} 로 유일하다 — upsert 의 조회 축. */
    Optional<MotifPassPrice> findByArtistSeqAndCountrySeqAndDurationMonths(
            Integer artistSeq, Integer countrySeq, short durationMonths);

    /**
     * 이 기간의 유효 PASS 수 — 가격 비활성화 시 돌려줄 <b>경고 카운트</b>(§3.9.1).
     *
     * <p><b>국가로 나누지 않는다.</b> {@code motif_passes} 에는 국가 칸이 없고 결제 시점
     * 국가는 {@code orders} 스냅샷에만 있다(DB §33·§35). 운영자가 보려는 것은 "이 값을
     * 내리면 몇 명이 쓰던 상품인가"이므로 (아티스트, 개월) 단위로 충분하다.
     *
     * <p>{@code motif_passes} 엔티티를 만들지 않고 네이티브로 센다 — 여기서 필요한 것은
     * 숫자 하나이고, 그 테이블의 상태 규칙(취소 의사와 이용 판정의 분리 — DB §35)은
     * 구매 도메인이 소유한다. 그쪽 티켓이 엔티티를 만들면 이 쿼리는 그리로 옮긴다.
     */
    @Query(value = """
            select count(*) from motif_core.motif_passes
             where artist_seq = :artistSeq and duration_months = :durationMonths
               and status = 'ACTIVE' and expires_dttm > now()
            """, nativeQuery = true)
    long countActivePasses(@Param("artistSeq") Integer artistSeq,
                           @Param("durationMonths") short durationMonths);

    /**
     * 내 유효 PASS 의 만료 시각 — 아티스트 상세의 {@code pass} (API 명세 §5.3).
     *
     * <p>{@code uq_motif_passes_user_artist} 가 (사용자, 아티스트)당 한 행을 보장하므로
     * 결과는 0 또는 1건이다. 값이 있으면 {@code active=true}, 비면 {@code false} 다 —
     * "유효" 판정({@code status='ACTIVE'} 이고 아직 만료 전)을 <b>DB 쪽에 두는 것이
     * 요점이다</b>. 행을 꺼내 와 자바에서 시각을 비교하면 앱 서버 시계가 DB 와 어긋난
     * 만큼 만료 직전·직후 판정이 갈린다.
     *
     * <p>{@code motif_passes} 엔티티를 만들지 않고 네이티브로 읽는 근거는
     * {@link #countActivePasses} 와 같다 — 구매 도메인 티켓이 엔티티를 만들면 두 쿼리를
     * 함께 그리로 옮긴다.
     */
    @Query(value = """
            select expires_dttm from motif_core.motif_passes
             where user_seq = :userSeq and artist_seq = :artistSeq
               and status = 'ACTIVE' and expires_dttm > now()
            """, nativeQuery = true)
    Optional<Instant> findActivePassExpiry(@Param("userSeq") Integer userSeq,
                                           @Param("artistSeq") Integer artistSeq);
}
