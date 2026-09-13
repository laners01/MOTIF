package com.ssafy.motif.domain.core.asset;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface MotifAssetPriceRepository extends JpaRepository<MotifAssetPrice, Integer> {

    /** {@code uq_motif_asset_prices_asset_country} 로 유일하다 — upsert(§3.8.6)의 조회 축. */
    Optional<MotifAssetPrice> findByAssetSeqAndCountrySeq(Integer assetSeq, Integer countrySeq);

    /** §3.8.5 응답의 경고 카운트 — 재활성화되지 않은 가격이 몇 개 남았는지. */
    long countByAssetSeqAndActiveFalse(Integer assetSeq);

    /**
     * 에셋 비활성화 연쇄 — 해당 에셋의 활성 가격을 전부 내린다 (DB §30).
     *
     * <p>행을 읽어 하나씩 끄지 않는 이유는 국가가 늘어나면 N 번 왕복이 되기 때문이다.
     * 벌크 UPDATE 는 영속성 컨텍스트를 우회하므로 두 플래그가 함께 필요하다 —
     * {@code flushAutomatically} 가 없으면 같은 트랜잭션에서 방금 바꾼
     * {@code motif_assets.is_active} 가 clear 로 <b>통째로 버려진다</b>
     * ({@code ArtistRepository#increaseSubscriberCount} 와 같은 함정).
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update MotifAssetPrice p
               set p.active = false, p.modUsrSeq = :modUsrSeq, p.modDttm = :now
             where p.assetSeq = :assetSeq and p.active = true
            """)
    int deactivateAll(@Param("assetSeq") Integer assetSeq,
                      @Param("modUsrSeq") Integer modUsrSeq,
                      @Param("now") Instant now);

    /**
     * 아티스트 삭제 연쇄 — 그 아티스트 에셋들의 활성 가격을 한 방에 내린다 (어드민 §3.5.5).
     *
     * <p>{@link #deactivateAll} 을 에셋마다 부르지 않는 이유는 왕복 수가 에셋 × 국가로
     * 늘기 때문이다. {@code motif_asset_prices} 에는 아티스트 칸이 없어
     * ({@code asset_seq} 로만 묶인다) 서브쿼리로 에셋을 건너 짚는다.
     *
     * <p><b>에셋 비활성화와 순서가 무관하다</b> — 서브쿼리가 {@code artist_seq} 로만
     * 고르고 {@code is_active} 를 보지 않는다. 순서에 의존했다면 두 UPDATE 사이에
     * 끼어드는 순간 가격만 살아남는 창이 생긴다.
     *
     * @return 이번 호출로 꺼진 가격 행 수
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update MotifAssetPrice p
               set p.active = false, p.modUsrSeq = :modUsrSeq, p.modDttm = :now
             where p.active = true
               and p.assetSeq in (select a.assetSeq from MotifAsset a where a.artistSeq = :artistSeq)
            """)
    int deactivateByArtist(@Param("artistSeq") Integer artistSeq,
                           @Param("modUsrSeq") Integer modUsrSeq,
                           @Param("now") Instant now);
}
