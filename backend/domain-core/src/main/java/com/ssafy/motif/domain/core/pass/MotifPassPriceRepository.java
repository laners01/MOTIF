package com.ssafy.motif.domain.core.pass;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
