package com.ssafy.motif.domain.core.pass;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

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

    /**
     * 구독 카드의 {@code passActive}·{@code passExpiresDttm} (API 명세 §4.7) —
     * 아티스트 여러 명을 한 번에.
     *
     * <p>{@link #findActivePassExpiry} 를 카드마다 부르면 그대로 N+1 이다. 판정 기준과
     * "유효 판정을 DB 에 둔다"는 근거는 그쪽과 같다 — 앱 서버 시계가 DB 와 어긋나면
     * 만료 직전·직후가 갈린다.
     *
     * <p>결과에 없는 아티스트는 유효 패스가 없는 것이다({@code passActive=false},
     * {@code passExpiresDttm=null}).
     */
    @Query(value = """
            select artist_seq as artistSeq, expires_dttm as expiresDttm
              from motif_core.motif_passes
             where user_seq = :userSeq
               and artist_seq in (:artistSeqs)
               and status = 'ACTIVE' and expires_dttm > now()
            """, nativeQuery = true)
    List<ActivePassProjection> findActivePasses(@Param("userSeq") Integer userSeq,
                                                @Param("artistSeqs") Collection<Integer> artistSeqs);

    /** 아티스트별 유효 패스 만료 — {@link #findActivePasses} 투영. */
    interface ActivePassProjection {
        Integer getArtistSeq();

        Instant getExpiresDttm();
    }
}
