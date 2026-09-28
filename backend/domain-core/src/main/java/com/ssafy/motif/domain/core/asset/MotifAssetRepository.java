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
import java.util.Optional;

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
     * {@code MotifPassRepository.countActivePasses} 가 같은 판단을 했다. 그쪽
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
     * 이 사용자가 이 에셋의 ACTIVE 개별 구매를 갖고 있나 — 주문 생성(API 명세 §12.1)의
     * {@code ALREADY_OWNED} 판정.
     *
     * <p>{@link #findDetail} 의 {@code owned} 와 같은 조건이지만 그쪽을 부르지 않는다 —
     * 상세는 노출 규칙(판매 중단 에셋은 구매자에게만) 때문에 비구매자에게 빈 결과를 주는데,
     * §12.1 은 그 경우를 404 가 아니라 409 {@code PRODUCT_NOT_ON_SALE} 로 답해야 하고(원본
     * 실패표), 아티스트·그룹 조인도 여기서는 필요 없다.
     *
     * <p>{@code asset_purchases} 엔티티 없이 네이티브로 묻는 근거는
     * {@link #countPurchasedByArtists} 와 같다 — 권한 행을 <b>만드는</b> 쪽(§12.2)이
     * 엔티티를 만들면 함께 옮긴다.
     */
    @Query(value = """
            select exists (select 1 from motif_core.asset_purchases p
                            where p.user_seq = :userSeq
                              and p.asset_seq = :assetSeq
                              and p.status = 'ACTIVE')
            """, nativeQuery = true)
    boolean existsActivePurchase(@Param("userSeq") Integer userSeq,
                                 @Param("assetSeq") Integer assetSeq);

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
     * 내가 구매한 에셋 (API 명세 §11.1) — <b>비페이지 전량</b>, 구매 최신순.
     *
     * <p><b>계약이 바뀌었다</b>(S15P21D104-219). 이 조회는 원래 "구독 아티스트의 에셋
     * 전량"({@code searchCatalog})이었고 숍 첫 화면을 그리는 자리였다. 그 자리는
     * {@link #searchShop}(§11.3)이 가져갔고, §11.1 은 <b>내 보유함</b>이 됐다 —
     * {@code docs/api/detail/11-motif.md} §11.1 에 사유가 있다.
     *
     * <p><b>{@code asset_purchases} 가 FROM 절이다.</b> 에셋에서 출발해 구매를
     * {@code exists} 로 거르면 "구매 최신순"을 낼 수 없다 — 정렬 기준이 되는
     * {@code reg_dttm} 이 서브쿼리 안에 갇힌다. 보유함은 "최근에 산 것" 순서가 자연스러운
     * 화면이라 구매 행을 기준 축으로 둔다.
     *
     * <p>동률을 {@code purchase_seq} 로 끊는다. 같은 초에 두 건을 사면
     * {@code reg_dttm} 만으로는 순서가 매번 달라진다.
     *
     * <p><b>보지 않는 것이 셋이다.</b>
     * <ul>
     *   <li>{@code is_active} — 판매가 중단돼도 산 것은 산 것이다(§4.7
     *       {@code purchasedAssetCount} 와 같은 규칙).</li>
     *   <li>구독 — 구독을 끊어도 산 것은 남는다.</li>
     *   <li>{@code artists.is_deleted} — 같은 이유다. 이름·프로필은 행이 남아 있어
     *       계속 조인된다.</li>
     * </ul>
     *
     * <p><b>패스는 여기 없다.</b> 패스로만 열리는 에셋은 산 것이 아니므로 대상이 아니고,
     * 결과가 전부 개별 구매라 {@code myAccess} 를 계산할 이유도 없다 — 값이 하나뿐인
     * 칸이 된다. 그 판정이 필요한 곳은 §11.2 상세와 §11.3 숍이다.
     *
     * <p>{@code cast(:artistSeq as integer)} 은 장식이 아니다 — 네이티브 쿼리에
     * {@code null} 을 그냥 바인딩하면 Postgres 가 파라미터 타입을 못 정해 쿼리 자체가
     * 실패한다. 별칭의 따옴표도 같은 이유다({@link #search} 참조).
     *
     * @param artistSeq {@code null} 이면 전체 — 값이 있으면 그 아티스트에게서 산 것만
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
                   a.asset_type        as "assetType"
              from motif_core.asset_purchases p
              join motif_core.motif_assets a on a.asset_seq = p.asset_seq
              join motif_core.artists ar on ar.artist_seq = a.artist_seq
              join motif_core.users u on u.user_seq = ar.user_seq
              left join motif_core.artist_groups g on g.group_seq = ar.group_seq
             where p.user_seq = :userSeq
               and p.status = 'ACTIVE'
               and (cast(:artistSeq as integer) is null
                    or a.artist_seq = cast(:artistSeq as integer))
             order by p.reg_dttm desc, p.purchase_seq desc
            """, nativeQuery = true)
    List<AssetPurchasedRow> findPurchased(@Param("userSeq") Integer userSeq,
                                          @Param("artistSeq") Integer artistSeq);

    /**
     * {@link #findPurchased} 한 행.
     *
     * <p>권한 칸이 없다 — 전부 개별 구매라 물을 것이 없다.
     */
    interface AssetPurchasedRow {

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
    }

    /**
     * 숍 전량 (API 명세 §11.3) — 아티스트 여러 명의 에셋을 <b>한 번에</b>, 최신순.
     *
     * <p><b>아티스트마다 부르면 그대로 N+1 이다.</b> 숍 첫 화면은 구독 수만큼 섹션을
     * 그리므로, 구독 30명이면 조회가 30번이 된다. 그것을 없애려고 되살린 API 에서
     * 같은 짓을 하면 의미가 없다.
     *
     * <p><b>조인이 없다.</b> 아티스트 이름·프로필·그룹명은 호출부가 이미
     * {@code SubscriptionRepository.findSubscribed} 로 갖고 있고(섹션 순서의 출처이기도
     * 하다), 패스 보유 여부도 {@code MotifPassRepository.findActivePasses} 가 따로
     * 답한다. 여기서 다시 조인하면 같은 값을 두 번 읽는다.
     *
     * <p>그래서 {@code passActive} 서브쿼리도 없다 — {@code myAccess} 는 이 결과의
     * {@code owned} 와 패스 조회 결과를 호출부가 합쳐 정한다. 패스는 <b>아티스트당
     * 하나</b>인데 에셋 행마다 다시 판정하면 같은 답을 에셋 수만큼 계산하게 된다.
     *
     * <p><b>비활성 에셋은 개별 구매자에게만 보인다</b>(§11.1 과 같은 노출 규칙).
     * {@code owned} 와 같은 모양의 {@code exists} 지만 하는 일이 다르다 — 저쪽은
     * "무슨 권한인가"이고 여기는 "보여 줄까"다.
     *
     * <p>{@code artistSeqs} 가 비면 부르지 않는다 — Postgres 에서 {@code in ()} 는 문법
     * 오류다. 호출부가 구독 0건을 먼저 걸러 빈 목록으로 답한다.
     */
    @Query(value = """
            select a.asset_seq     as "assetSeq",
                   a.artist_seq    as "artistSeq",
                   a.title         as "title",
                   a.description   as "description",
                   a.thumbnail_key as "thumbnailKey",
                   a.duration_ms   as "durationMs",
                   a.asset_type    as "assetType",
                   exists (select 1 from motif_core.asset_purchases p
                            where p.asset_seq = a.asset_seq
                              and p.user_seq = :userSeq
                              and p.status = 'ACTIVE')            as "owned"
              from motif_core.motif_assets a
             where a.artist_seq in (:artistSeqs)
               and (a.is_active = true
                    or exists (select 1 from motif_core.asset_purchases ap
                                where ap.asset_seq = a.asset_seq
                                  and ap.user_seq = :userSeq
                                  and ap.status = 'ACTIVE'))
             order by a.asset_seq desc
            """, nativeQuery = true)
    List<AssetShopRow> searchShop(@Param("userSeq") Integer userSeq,
                                  @Param("artistSeqs") Collection<Integer> artistSeqs);

    /**
     * {@link #searchShop} 한 행.
     *
     * <p>{@code artistSeq} 는 응답 값이 아니라 <b>그룹핑 축</b>이다 — 호출부가 이 값으로
     * 섹션에 나눠 담는다.
     */
    interface AssetShopRow {

        Integer getAssetSeq();

        Integer getArtistSeq();

        String getTitle();

        /** 소개 문구 — {@code title} 과 다른 칸이고 {@code null} 일 수 있다. */
        String getDescription();

        String getThumbnailKey();

        Integer getDurationMs();

        String getAssetType();

        /** 개별 구매 보유 여부. 패스는 보지 않는다 — 그쪽은 아티스트 단위로 따로 온다. */
        Boolean getOwned();
    }

    /**
     * 팬 앱 에셋 상세 (API 명세 §11.2) — 단건.
     *
     * <p><b>노출 규칙은 {@link #searchShop} 과 같다.</b> 판매 중단
     * ({@code is_active=FALSE}) 에셋은 개별 구매자에게만 보인다 — 패스만 있어도 보이지
     * 않는다. 숍에서 안 보이던 것이 상세로는 열리면 노출 규칙이 두 벌이 된다.
     *
     * <p><b>빈 결과가 곧 404 다.</b> "미존재"와 "판매 중단 + 비구매자"를 쿼리가 구분하지
     * 않는 것은 의도다(§1.8 존재 은닉) — 둘을 가르면 403 과 404 가 갈리면서 "그 에셋은
     * 존재한다"가 응답에서 읽힌다.
     *
     * <p><b>구독을 보지 않는다.</b> 상세는 아티스트 상세(§5)·딥링크로 들어오는 자리라
     * 미구독이 정상이다 — 구독을 축으로 삼는 것은 숍({@link #searchShop})뿐이다.
     *
     * <p>가격은 <b>KR 활성 행 하나만</b> 붙인다({@code and p.is_active = true}). 어드민
     * {@link #search} 는 {@code priceActive} 를 따로 내보내 운영자가 꺼진 가격을 보게
     * 하지만, 팬에게는 꺼진 가격이 없는 것과 같다. {@code left join} 이라 가격 없는
     * 에셋도 상세가 열린다 — 그때 {@code purchasable} 이 {@code false} 다.
     *
     * <p>목록과 SQL 이 닮았지만 합치지 않는다. 저쪽은 페이지·정렬이 있고 이쪽은 가격
     * 조인이 붙는다 — 한 쿼리로 묶으면 목록이 항목마다 가격을 조인하게 된다.
     */
    @Query(value = """
            select a.asset_seq         as "assetSeq",
                   a.artist_seq        as "artistSeq",
                   u.nickname          as "artistName",
                   g.group_name        as "groupName",
                   u.profile_image_key as "artistProfileImageKey",
                   a.title             as "title",
                   a.description       as "description",
                   a.video_key         as "videoKey",
                   a.thumbnail_key     as "thumbnailKey",
                   a.duration_ms       as "durationMs",
                   a.asset_type        as "assetType",
                   a.is_active         as "active",
                   p.amount            as "priceAmount",
                   p.currency          as "priceCurrency",
                   exists (select 1 from motif_core.asset_purchases ap
                            where ap.asset_seq = a.asset_seq
                              and ap.user_seq = :userSeq
                              and ap.status = 'ACTIVE')            as "owned",
                   exists (select 1 from motif_core.motif_passes mp
                            where mp.user_seq = :userSeq
                              and mp.artist_seq = a.artist_seq
                              and mp.status = 'ACTIVE'
                              and mp.expires_dttm > current_timestamp) as "passActive"
              from motif_core.motif_assets a
              join motif_core.artists ar on ar.artist_seq = a.artist_seq
              join motif_core.users u on u.user_seq = ar.user_seq
              left join motif_core.artist_groups g on g.group_seq = ar.group_seq
              left join motif_core.motif_asset_prices p
                     on p.asset_seq = a.asset_seq
                    and p.is_active = true
                    and p.country_seq = (select c.country_seq from motif_core.countries c
                                          where c.country_code = :countryCode)
             where a.asset_seq = :assetSeq
               and (a.is_active = true
                    or exists (select 1 from motif_core.asset_purchases ap2
                                where ap2.asset_seq = a.asset_seq
                                  and ap2.user_seq = :userSeq
                                  and ap2.status = 'ACTIVE'))
            """, nativeQuery = true)
    Optional<AssetDetailRow> findDetail(@Param("userSeq") Integer userSeq,
                                        @Param("assetSeq") Integer assetSeq,
                                        @Param("countryCode") String countryCode);

    /**
     * {@link #findDetail} 한 행.
     *
     * <p>{@code videoKey} 는 <b>가공 단계에 따라 가리키는 것이 다르다</b> — 등록 직후에는
     * 업로드 MP4({@code assets/{uuid}.mp4}), 가공이 끝나면 app-media 가 마스터 재생목록
     * ({@code assets/{uuid}/master.m3u8})으로 덮어쓴다. 어느 쪽이든 {@code assets/}
     * 프리픽스라 공개 등급이고, 호출부는 키를 조립하지 않고 이 값을 그대로 URL 로 바꾼다.
     *
     * <p>가격 두 칸은 KR 활성 행이 없으면 {@code null} 이다. {@code active} 는 응답 값이
     * 아니라 {@code purchasable} 판정 재료다 — 판매 중단 에셋은 구매자에게만 보이는데,
     * 보인다고 다시 살 수 있는 것은 아니다.
     */
    interface AssetDetailRow {

        Integer getAssetSeq();

        Integer getArtistSeq();

        String getArtistName();

        /** 솔로 아티스트는 {@code null} (README §3.5). */
        String getGroupName();

        String getArtistProfileImageKey();

        String getTitle();

        String getDescription();

        String getVideoKey();

        String getThumbnailKey();

        Integer getDurationMs();

        String getAssetType();

        /** 판매 중 여부 — {@code purchasable} 판정에만 쓰고 응답에는 없다. */
        Boolean getActive();

        BigDecimal getPriceAmount();

        String getPriceCurrency();

        Boolean getOwned();

        Boolean getPassActive();
    }
}
