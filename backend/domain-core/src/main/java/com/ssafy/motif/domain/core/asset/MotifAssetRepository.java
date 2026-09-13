package com.ssafy.motif.domain.core.asset;

import com.ssafy.motif.domain.core.artist.ArtistCount;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface MotifAssetRepository extends JpaRepository<MotifAsset, Integer> {

    /**
     * 아티스트 목록·상세의 {@code assetCount} (어드민 §3.5.1) — 페이지 전체를 한 번에.
     *
     * <p>비활성 에셋도 센다. {@code motif_assets} 에는 삭제 플래그가 없고(DB §30)
     * 운영 지표로 보고 싶은 것은 "이 아티스트가 가진 에셋 수" 이기 때문이다.
     */
    @Query("""
            select new com.ssafy.motif.domain.core.artist.ArtistCount(a.artistSeq, count(a))
            from MotifAsset a
            where a.artistSeq in :artistSeqs
            group by a.artistSeq
            """)
    List<ArtistCount> countByArtists(@Param("artistSeqs") Collection<Integer> artistSeqs);

    /**
     * 어드민 목록 (어드민 명세 §3.8.1) — 아티스트·활성·타입 필터, offset 페이지.
     *
     * <p><b>네이티브다.</b> 항목이 요구하는 {@code groupName}·{@code purchaseCount}·
     * {@code resultCount} 가 전부 엔티티 없는 테이블({@code artist_groups} ·
     * {@code asset_purchases} · {@code motif_results})에 있다. 표시용 숫자 두 개를 위해
     * 구매·결과물 도메인을 통째로 매핑할 이유가 없다 —
     * {@code ChallengeRepository#countActiveJobs} 와 같은 판단이다.
     *
     * <p>가격은 <b>KR 한 행만</b> 붙인다(§3.8.1 — "{@code price} 는 KR 행이 없으면 null").
     * {@code left join} 이라 가격 없는 에셋도 목록에서 빠지지 않는다.
     *
     * <p>{@code cast(:param as ...)} 은 장식이 아니다 — 네이티브 쿼리에 {@code null} 을
     * 바인딩하면 Postgres 가 파라미터 타입을 못 정해 쿼리 자체가 실패한다.
     *
     * <p>별칭에 따옴표를 두른 이유는 Postgres 가 따옴표 없는 식별자를 전부 소문자로
     * 내리기 때문이다 — 그대로 두면 {@code assetSeq} 가 {@code assetseq} 로 와서
     * 프로젝션 게터와 맞지 않는다.
     */
    @Query(value = """
            select a.asset_seq     as "assetSeq",
                   a.artist_seq    as "artistSeq",
                   u.nickname      as "artistName",
                   g.group_name    as "groupName",
                   a.title         as "title",
                   a.thumbnail_key as "thumbnailKey",
                   a.duration_ms   as "durationMs",
                   a.asset_type    as "assetType",
                   a.is_active     as "active",
                   p.amount        as "priceAmount",
                   p.currency      as "priceCurrency",
                   p.is_active     as "priceActive",
                   (select count(*) from motif_core.asset_purchases ap
                     where ap.asset_seq = a.asset_seq and ap.status = 'ACTIVE') as "purchaseCount",
                   (select count(*) from motif_core.motif_results r
                     where r.asset_seq = a.asset_seq and r.is_deleted = false)  as "resultCount",
                   a.reg_dttm      as "regDttm"
              from motif_core.motif_assets a
              join motif_core.artists ar on ar.artist_seq = a.artist_seq
              join motif_core.users u on u.user_seq = ar.user_seq
              left join motif_core.artist_groups g on g.group_seq = ar.group_seq
              left join motif_core.motif_asset_prices p
                     on p.asset_seq = a.asset_seq
                    and p.country_seq = (select c.country_seq from motif_core.countries c
                                          where c.country_code = :countryCode)
             where (cast(:artistSeq as integer) is null or a.artist_seq = cast(:artistSeq as integer))
               and (cast(:isActive as boolean) is null or a.is_active = cast(:isActive as boolean))
               and (cast(:assetType as varchar) is null or a.asset_type = cast(:assetType as varchar))
             order by a.asset_seq desc
            """,
            countQuery = """
            select count(*) from motif_core.motif_assets a
             where (cast(:artistSeq as integer) is null or a.artist_seq = cast(:artistSeq as integer))
               and (cast(:isActive as boolean) is null or a.is_active = cast(:isActive as boolean))
               and (cast(:assetType as varchar) is null or a.asset_type = cast(:assetType as varchar))
            """,
            nativeQuery = true)
    Page<AssetListRow> search(@Param("artistSeq") Integer artistSeq,
                              @Param("isActive") Boolean isActive,
                              @Param("assetType") String assetType,
                              @Param("countryCode") String countryCode,
                              Pageable pageable);

    /** {@link #search} 한 행 — 가격 3종은 KR 행이 없으면 {@code null} 이다. */
    interface AssetListRow {

        Integer getAssetSeq();

        Integer getArtistSeq();

        String getArtistName();

        String getGroupName();

        String getTitle();

        String getThumbnailKey();

        Integer getDurationMs();

        String getAssetType();

        Boolean getActive();

        BigDecimal getPriceAmount();

        String getPriceCurrency();

        Boolean getPriceActive();

        Long getPurchaseCount();

        Long getResultCount();

        Instant getRegDttm();
    }
}
