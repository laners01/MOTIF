package com.ssafy.motif.domain.core.asset;

import com.ssafy.motif.domain.core.artist.ArtistCount;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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
     * 구독 카드의 {@code totalAssetCount} (API 명세 §4.7) — <b>판매 중인</b> 에셋만.
     *
     * <p>{@link #countByArtists} 와 나란히 두는 이유는 <b>세는 대상이 다르기 때문</b>이다.
     * 그쪽은 어드민 지표라 비활성까지 세고(§3.5.1), 여기는 팬 화면의 분모라
     * {@code is_active=TRUE} 만이다 — 내려간 에셋을 분모에 넣으면 "12개 중 2개"가
     * 영원히 채워지지 않는다.
     *
     * <p><b>{@code SECRET} 도 함께 센다</b>(명세 §4.7). 숨은 에셋이 있다는 사실 자체는
     * 숍에서 드러나는 정보다.
     *
     * <p>이 값이 {@code purchasedAssetCount} 보다 <b>작아질 수 있다</b> — 판매 중단된
     * 에셋을 이미 구매한 경우다. 서버는 보정하지 않는다(원본 §16 #6 — 표시 방법이 클라
     * 협의 대상).
     */
    @Query("""
            select new com.ssafy.motif.domain.core.artist.ArtistCount(a.artistSeq, count(a))
            from MotifAsset a
            where a.artistSeq in :artistSeqs and a.active = true
            group by a.artistSeq
            """)
    List<ArtistCount> countActiveByArtists(@Param("artistSeqs") Collection<Integer> artistSeqs);

    /**
     * 구독 카드의 {@code purchasedAssetCount} (API 명세 §4.7) — <b>개별 구매만</b>.
     *
     * <p><b>패스로 열린 에셋은 세지 않는다.</b> 패스만 있는 사용자는 모든 에셋을 쓸 수
     * 있어도 {@code 0/m} 이다(명세 §4.7). 카드의 {@code n/m} 은 "몇 개를 샀나"이지
     * "몇 개를 쓸 수 있나"가 아니다 — 패스 상태는 같은 카드의 {@code passActive} 가
     * 따로 답한다.
     *
     * <p><b>분모({@link #countActiveByArtists})와 달리 {@code is_active} 를 보지 않는다.</b>
     * 산 것은 판매가 중단돼도 산 것이다. 그래서 분자가 분모를 넘을 수 있다.
     *
     * <p>{@code asset_purchases} 엔티티를 만들지 않고 네이티브로 센다 — 여기서 필요한
     * 것은 숫자뿐이고 그 테이블의 상태 규칙(환불 처리)은 구매 도메인(§12)이 소유한다.
     * {@code MotifPassPriceRepository.countActivePasses} 가 같은 판단을 했다. 그쪽
     * 티켓이 엔티티를 만들면 이 쿼리는 그리로 옮긴다.
     */
    @Query(value = """
            select a.artist_seq as artistSeq, count(*) as count
              from motif_core.asset_purchases p
              join motif_core.motif_assets a on a.asset_seq = p.asset_seq
             where p.user_seq = :userSeq
               and a.artist_seq in (:artistSeqs)
               and p.status = 'ACTIVE'
             group by a.artist_seq
            """, nativeQuery = true)
    List<ArtistCountProjection> countPurchasedByArtists(@Param("userSeq") Integer userSeq,
                                                        @Param("artistSeqs") Collection<Integer> artistSeqs);

    /** 네이티브 결과 투영 — JPQL 의 {@code ArtistCount} 와 같은 모양이다. */
    interface ArtistCountProjection {
        Integer getArtistSeq();

        long getCount();
    }

    /**
     * 아티스트 삭제 연쇄 — 그 아티스트의 활성 에셋을 전부 내린다 (어드민 §3.5.5).
     *
     * <p>{@code motif_assets} 에는 삭제 플래그가 없어({@link MotifAsset} javadoc)
     * 종료는 {@code is_active} 로만 표현한다. 이미 꺼진 행은 건드리지 않는다 —
     * 응답의 {@code deactivatedAssets} 는 <b>이번에 실제로 꺼진 수</b>여야 하고,
     * 그래야 재삭제·부분 실패를 숫자로 구분할 수 있다.
     *
     * <p>벌크라 {@code @PreUpdate} 가 돌지 않으므로 {@code modDttm} 을 직접 넣는다.
     * 두 플래그가 함께 있어야 하는 이유는
     * {@link MotifAssetPriceRepository#deactivateAll} 과 같다 — {@code flushAutomatically}
     * 가 없으면 같은 트랜잭션에서 방금 세운 {@code artists.is_deleted} 가 clear 로
     * 통째로 버려진다.
     *
     * @return 이번 호출로 꺼진 에셋 수
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update MotifAsset a
               set a.active = false, a.modDttm = :now
             where a.artistSeq = :artistSeq and a.active = true
            """)
    int deactivateByArtist(@Param("artistSeq") Integer artistSeq, @Param("now") Instant now);

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
                   a.description   as "description",
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
             where (cast(:assetSeq as integer) is null or a.asset_seq = cast(:assetSeq as integer))
               and (cast(:artistSeq as integer) is null or a.artist_seq = cast(:artistSeq as integer))
               and (cast(:isActive as boolean) is null or a.is_active = cast(:isActive as boolean))
               and (cast(:assetType as varchar) is null or a.asset_type = cast(:assetType as varchar))
             order by a.asset_seq desc
            """,
            countQuery = """
            select count(*) from motif_core.motif_assets a
             where (cast(:assetSeq as integer) is null or a.asset_seq = cast(:assetSeq as integer))
               and (cast(:artistSeq as integer) is null or a.artist_seq = cast(:artistSeq as integer))
               and (cast(:isActive as boolean) is null or a.is_active = cast(:isActive as boolean))
               and (cast(:assetType as varchar) is null or a.asset_type = cast(:assetType as varchar))
            """,
            nativeQuery = true)
    Page<AssetListRow> search(@Param("assetSeq") Integer assetSeq,
                              @Param("artistSeq") Integer artistSeq,
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

        /** §3.8.3 상세에만 쓰인다 — 목록 항목(§3.8.1)에는 없는 값이다. */
        String getDescription();

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

    /**
     * 팬 앱 에셋 목록 (API 명세 §11.1) — offset 페이지, 최신순 고정.
     *
     * <p><b>{@code artistSeq} 가 모드를 가른다.</b> {@code null} 이면 <b>내가 구독한
     * 아티스트</b>의 에셋만, 값이 있으면 구독 여부를 보지 않고 그 아티스트의 에셋만이다.
     * 원본 명세 §11.1 은 "생략하면 전체 아티스트"였지만 숍의 기본 화면이 구독 기반이라
     * 기본값을 바꿨다(S15P21D104-224 — {@code docs/api/detail/11-motif.md} §11.1 에 사유).
     * 전체 카탈로그를 한 번에 주는 경로는 두지 않는다.
     *
     * <p><b>왜 어드민 {@link #search} 와 나누나.</b> 저쪽은 운영 지표(구매 수·결과물 수·
     * 가격)를 붙이고 비활성 에셋을 그대로 보여 준다. 여기는 그 세 값을 쓰지 않는 대신
     * 호출자별 판정(구독·구매·패스)이 붙는다 — 합치면 어느 화면에도 필요 없는 칸을
     * 서로에게 계산해 주는 쿼리가 된다.
     *
     * <p><b>세 판정을 전부 쿼리 안에서 한다.</b> 구독 필터·노출 필터·{@code myAccess} 는
     * 모두 <b>페이지 대상 집합</b>을 바꾼다. 하나라도 애플리케이션에서 거르면
     * {@code totalElements} 와 페이지 경계가 응답 항목 수와 어긋난다 — 20건을 요청했는데
     * 3건만 오면서 {@code hasNext=true} 인 목록이 된다.
     *
     * <p><b>비활성 에셋은 개별 구매자에게만 보인다</b>(§11.1). 패스만 있는 사용자에게는
     * 보이지 않으므로 노출 조건의 {@code exists} 는 구매만 본다 — {@code owned} 와 같은
     * 서브쿼리지만 <b>하는 일이 다르다</b>. 저쪽은 "보여 줄까"이고 이쪽은 "무슨 권한인가"라,
     * 노출 규칙이 바뀌어도 {@code myAccess} 는 그대로여야 한다.
     *
     * <p><b>패스 만료는 {@code current_timestamp} 로 비교한다</b> — DB 가 평가한다.
     * 자바에서 시각을 꺼내 넘기면 앱 서버와 DB 의 시계 차만큼 만료 직전·직후 판정이
     * 갈린다({@code MotifPassRepository.findActive} 와 같은 근거).
     *
     * <p><b>{@code PREMIUM} 의 패스 미커버는 여기서 판정하지 않는다.</b> 쿼리는 "그
     * 아티스트의 유효 패스가 있는가"({@code passActive})까지만 답하고, 타입별 커버 규칙은
     * 서비스가 {@code assetType} 과 함께 본다 — DB 없이 검증할 수 있는 자리에 둔다.
     *
     * <p>구독 모드에서 <b>삭제 아티스트를 뺀다</b>. 구독 행은 아티스트가 삭제돼도 남으므로
     * ({@code SubscriptionRepository.findSubscribed} 와 같은 근거) 거르지 않으면 눌러도
     * 404 가 나는 카드가 섞인다. {@code artistSeq} 모드에는 이 게이트가 없다 — 삭제
     * 연쇄로 에셋이 전부 {@code is_active=FALSE} 가 되어 개별 구매자에게만 남는데,
     * 산 것은 판매가 끊겨도 산 것이다(§4.7 {@code purchasedAssetCount} 와 같은 규칙).
     *
     * <p>{@code cast(:artistSeq as integer)} 은 장식이 아니다 — 네이티브 쿼리에
     * {@code null} 을 그냥 바인딩하면 Postgres 가 파라미터 타입을 못 정해 쿼리 자체가
     * 실패한다. 별칭의 따옴표도 같은 이유다({@link #search} 참조).
     *
     * @param artistSeq {@code null} 이면 구독 아티스트 전체
     */
    @Query(value = """
            select a.asset_seq         as "assetSeq",
                   a.artist_seq        as "artistSeq",
                   u.nickname          as "artistName",
                   g.group_name        as "groupName",
                   u.profile_image_key as "artistProfileImageKey",
                   a.title             as "title",
                   a.description       as "description",
                   a.thumbnail_key     as "thumbnailKey",
                   a.duration_ms       as "durationMs",
                   a.asset_type        as "assetType",
                   exists (select 1 from motif_core.asset_purchases p
                            where p.asset_seq = a.asset_seq
                              and p.user_seq = :userSeq
                              and p.status = 'ACTIVE')            as "owned",
                   exists (select 1 from motif_core.motif_passes mp
                            where mp.user_seq = :userSeq
                              and mp.artist_seq = a.artist_seq
                              and mp.status = 'ACTIVE'
                              and mp.expires_dttm > current_timestamp) as "passActive"
              from motif_core.motif_assets a
              join motif_core.artists ar on ar.artist_seq = a.artist_seq
              join motif_core.users u on u.user_seq = ar.user_seq
              left join motif_core.artist_groups g on g.group_seq = ar.group_seq
             where (cast(:artistSeq as integer) is null or a.artist_seq = cast(:artistSeq as integer))
               and (cast(:artistSeq as integer) is not null
                    or (ar.is_deleted = false
                        and exists (select 1 from motif_core.subscriptions s
                                     where s.user_seq = :userSeq
                                       and s.artist_seq = a.artist_seq)))
               and (a.is_active = true
                    or exists (select 1 from motif_core.asset_purchases ap
                                where ap.asset_seq = a.asset_seq
                                  and ap.user_seq = :userSeq
                                  and ap.status = 'ACTIVE'))
             order by a.asset_seq desc
            """,
            countQuery = """
            select count(*)
              from motif_core.motif_assets a
              join motif_core.artists ar on ar.artist_seq = a.artist_seq
             where (cast(:artistSeq as integer) is null or a.artist_seq = cast(:artistSeq as integer))
               and (cast(:artistSeq as integer) is not null
                    or (ar.is_deleted = false
                        and exists (select 1 from motif_core.subscriptions s
                                     where s.user_seq = :userSeq
                                       and s.artist_seq = a.artist_seq)))
               and (a.is_active = true
                    or exists (select 1 from motif_core.asset_purchases ap
                                where ap.asset_seq = a.asset_seq
                                  and ap.user_seq = :userSeq
                                  and ap.status = 'ACTIVE'))
            """,
            nativeQuery = true)
    Page<AssetCatalogRow> searchCatalog(@Param("userSeq") Integer userSeq,
                                        @Param("artistSeq") Integer artistSeq,
                                        Pageable pageable);

    /**
     * {@link #searchCatalog} 한 행.
     *
     * <p>{@code owned}·{@code passActive} 는 <b>판정 재료</b>지 응답 값이 아니다 —
     * 둘을 {@code myAccess} 하나로 접는 일은 서비스가 한다(§11.1 우선순위 OWNED &gt; PASS
     * &gt; NONE).
     */
    interface AssetCatalogRow {

        Integer getAssetSeq();

        Integer getArtistSeq();

        String getArtistName();

        /** 솔로 아티스트는 {@code null} (README §3.5). */
        String getGroupName();

        String getArtistProfileImageKey();

        String getTitle();

        /** 소개 문구 — {@code title} 과 다른 칸이고 {@code null} 일 수 있다. */
        String getDescription();

        String getThumbnailKey();

        Integer getDurationMs();

        String getAssetType();

        /** 개별 구매 보유 여부 — {@code getActive()} 와 같은 이유로 래퍼 게터다(투영 이름 매칭). */
        Boolean getOwned();

        /** 그 아티스트의 유효 패스 보유 여부. 에셋 타입은 보지 않는다. */
        Boolean getPassActive();
    }
}
