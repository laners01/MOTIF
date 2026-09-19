package com.ssafy.motif.domain.core.pass;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * PASS <b>가격</b> 조회 — 보유한 패스({@code motif_passes})는 다루지 않는다.
 *
 * <p>유효 패스 판정이 필요하면 {@link MotifPassRepository} 다. 한때 이 인터페이스가
 * {@code motif_passes} 네이티브 조회를 들고 있었으나 엔티티가 생기면 옮기기로 한 약속대로
 * 전부 그쪽으로 갔다(S15P21D104-269).
 */
public interface MotifPassPriceRepository extends JpaRepository<MotifPassPrice, Integer> {

    /** §3.9.1 — 아티스트 가격표(전 국가·기간). 화면이 국가×개월 그리드로 펼친다. */
    List<MotifPassPrice> findByArtistSeqOrderByCountrySeqAscDurationMonthsAsc(Integer artistSeq);

    /** {@code uq_motif_pass_prices_artist_country_duration} 로 유일하다 — upsert 의 조회 축. */
    Optional<MotifPassPrice> findByArtistSeqAndCountrySeqAndDurationMonths(
            Integer artistSeq, Integer countrySeq, short durationMonths);

    /**
     * 팬 앱 패스 화면의 {@code prices} (API 명세 §11.4) — <b>한 국가의 판매 중인 가격만</b>,
     * 개월 오름차순.
     *
     * <p>{@link #findByArtistSeqOrderByCountrySeqAscDurationMonthsAsc} 와 나란히 두는
     * 이유는 <b>보여 주는 대상이 다르기 때문</b>이다. 그쪽은 어드민 가격표(§3.9.1)라
     * 전 국가·꺼진 행까지 그리드로 펼쳐야 하고, 여기는 구매 화면이라 <b>지금 살 수 있는
     * 것</b>만이다. 꺼진 가격을 내보내면 누를 수 없는 버튼이 생긴다.
     *
     * <p>정렬이 개월순인 것은 화면이 1개월·3개월·6개월을 그 순서로 늘어놓기 때문이다 —
     * 금액순으로 두면 국가에 따라 순서가 뒤집힌다.
     */
    List<MotifPassPrice> findByArtistSeqAndCountrySeqAndActiveTrueOrderByDurationMonthsAsc(
            Integer artistSeq, Integer countrySeq);

    /**
     * 아티스트 삭제 연쇄 — 그 아티스트의 활성 PASS 가격을 전부 내린다 (어드민 §3.5.5).
     *
     * <p>이미 팔린 PASS 는 건드리지 않는다 — 이 행은 "지금 파는 값"이고 취득한 PASS 의
     * 금액·만료는 {@code orders} 스냅샷이 갖는다({@link MotifPassPrice} javadoc).
     * 삭제된 아티스트의 PASS 를 <b>새로 팔지 못하게</b> 막는 것이 여기서 하는 일의 전부다.
     *
     * <p>벌크 UPDATE 의 두 플래그는 {@code MotifAssetPriceRepository#deactivateAll} 과
     * 같은 이유로 함께 있어야 한다.
     *
     * @return 이번 호출로 꺼진 가격 행 수
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update MotifPassPrice p
               set p.active = false, p.modUsrSeq = :modUsrSeq, p.modDttm = :now
             where p.artistSeq = :artistSeq and p.active = true
            """)
    int deactivateByArtist(@Param("artistSeq") Integer artistSeq,
                           @Param("modUsrSeq") Integer modUsrSeq,
                           @Param("now") Instant now);

}
