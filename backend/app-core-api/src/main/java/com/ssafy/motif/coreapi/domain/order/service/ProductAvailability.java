package com.ssafy.motif.coreapi.domain.order.service;

import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.coreapi.domain.artist.ArtistErrorCode;
import com.ssafy.motif.coreapi.domain.asset.AssetErrorCode;
import com.ssafy.motif.coreapi.domain.asset.dto.MyAccess;
import com.ssafy.motif.coreapi.domain.order.OrderErrorCode;
import com.ssafy.motif.domain.core.artist.ArtistRepository;
import com.ssafy.motif.domain.core.asset.MotifAsset;
import com.ssafy.motif.domain.core.asset.MotifAssetPrice;
import com.ssafy.motif.domain.core.asset.MotifAssetPriceRepository;
import com.ssafy.motif.domain.core.asset.MotifAssetRepository;
import com.ssafy.motif.domain.core.country.CountryRepository;
import com.ssafy.motif.domain.core.pass.MotifPassPrice;
import com.ssafy.motif.domain.core.pass.MotifPassPriceRepository;
import com.ssafy.motif.domain.core.pass.MotifPassRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 상품이 이 회원에게 <b>지금 팔릴 수 있는가</b> (API 명세 §12.1 · §12.2 4단계).
 *
 * <p>주문 생성(§12.1)과 승인 직전 재검증(§12.2)이 <b>같은 판정</b>을 쓴다. 둘이 갈리면
 * "만들 때는 됐는데 승인할 때는 안 되는" 이유를 설명할 수 없고, 12.1 이 같은 상품의
 * {@code PENDING} 주문을 여러 개 허용하므로 재검증이 없으면 두 번째 승인이
 * {@code uq_asset_purchases_active} 에서 터진다.
 *
 * <p><b>판정 순서는 실패표 순서다</b> — 존재(404) → 판매 가능(409 {@code PRODUCT_NOT_ON_SALE})
 * → 보유·패스(409). 판매 중단 에셋은 §11.2 상세와 달리 <b>숨기지 않고</b> 409 로 답한다
 * (원본 §12.1 실패표가 {@code is_active=FALSE} 를 409 로 못박았다).
 *
 * <p><b>ASSET 의 보유·패스 판정은 {@link MyAccess#of} 다.</b> §11.1·§11.2·§11.3 이 쓰는 그
 * 규칙({@code OWNED > PASS > NONE}, {@code PREMIUM} 은 패스로 열리지 않는다)을 여기서 다시
 * 쓰면 상세 화면은 {@code PASS} 라는데 주문은 열리는 순간이 생긴다.
 *
 * <p>가격 행을 돌려주는 이유는 12.1 이 그 값을 스냅샷하기 때문이다. 12.2 는 판정만 쓰고
 * 가격은 버린다 — 승인 금액은 이미 주문에 스냅샷돼 있다(가격이 바뀌어도 만료 전에는 스냅샷
 * 가격으로 결제된다 — DB §33).
 */
@Component
@RequiredArgsConstructor
class ProductAvailability {

    /**
     * 가격을 고를 국가 (§12.1).
     *
     * <p>국가 협상이 아직 없어 KR 고정이다. {@code AssetQueryService}·
     * {@code ArtistMotifPassQueryService} 에도 같은 값이 있지만 <b>공유하지 않는다</b> —
     * 나라가 늘면 조회는 표시 국가로, 주문은 결제 국가로 따로 정해질 값이다.
     * {@code orders.country_code} 에 이 값이 그대로 스냅샷된다.
     */
    private static final String PRICE_COUNTRY_CODE = "KR";

    private final MotifAssetRepository motifAssetRepository;
    private final MotifAssetPriceRepository motifAssetPriceRepository;
    private final MotifPassPriceRepository motifPassPriceRepository;
    private final MotifPassRepository motifPassRepository;
    private final ArtistRepository artistRepository;
    private final CountryRepository countryRepository;

    String priceCountryCode() {
        return PRICE_COUNTRY_CODE;
    }

    /**
     * ASSET — 존재 → 판매 중 + KR 활성 가격 → 보유·패스.
     *
     * <p>패스 조회는 개별 구매가 없을 때만 한다. {@link MyAccess#of} 가 {@code OWNED} 를
     * 먼저 답하므로 보유자에게 패스를 물을 이유가 없다.
     *
     * @throws BusinessException 404 {@code ASSET_NOT_FOUND} · 409 {@code PRODUCT_NOT_ON_SALE} /
     *                           {@code ALREADY_OWNED} / {@code COVERED_BY_PASS}
     */
    AssetOffer assetOnSale(Integer userSeq, Integer assetSeq) {
        MotifAsset asset = motifAssetRepository.findById(assetSeq)
                .orElseThrow(() -> new BusinessException(
                        AssetErrorCode.ASSET_NOT_FOUND, "assetSeq: " + assetSeq));
        if (!asset.isActive()) {
            throw notOnSale("판매 중단 에셋 assetSeq: " + assetSeq);
        }
        MotifAssetPrice price = motifAssetPriceRepository
                .findByAssetSeqAndCountrySeq(assetSeq, priceCountrySeq())
                .filter(MotifAssetPrice::isActive)
                .orElseThrow(() -> notOnSale("KR 활성 가격 없음 assetSeq: " + assetSeq));

        boolean owned = motifAssetRepository.existsActivePurchase(userSeq, assetSeq);
        boolean passActive = !owned && motifPassRepository
                .findActivePassExpiry(userSeq, asset.getArtistSeq()).isPresent();
        switch (MyAccess.of(owned, passActive, asset.getAssetType())) {
            case OWNED -> throw new BusinessException(
                    OrderErrorCode.ALREADY_OWNED, "assetSeq: " + assetSeq);
            case PASS -> throw new BusinessException(
                    OrderErrorCode.COVERED_BY_PASS, "assetSeq: " + assetSeq);
            case NONE -> { }
        }
        return new AssetOffer(asset, price);
    }

    /**
     * PASS — 존재(비삭제) → 그 개월의 KR 활성 가격 → 유효 패스 없음.
     *
     * <p>유효 패스 판정은 {@code MotifPassRepository.findActivePassExpiry} 하나다 —
     * §5.3·§11.4 가 쓰는 것과 같은 메서드라, 구매 화면이 "미보유" 라고 보여 준 사람의 주문이
     * 여기서 막히는 일이 없다. 만료·환불된 패스는 그 조회에 잡히지 않으므로 재구매가 열린다.
     *
     * @throws BusinessException 404 {@code ARTIST_NOT_FOUND} · 409 {@code PRODUCT_NOT_ON_SALE} /
     *                           {@code PASS_ALREADY_ACTIVE}
     */
    PassOffer passOnSale(Integer userSeq, Integer artistSeq, short months) {
        artistRepository.findByArtistSeqAndDeletedFalse(artistSeq)
                .orElseThrow(() -> new BusinessException(
                        ArtistErrorCode.ARTIST_NOT_FOUND, "artistSeq: " + artistSeq));
        MotifPassPrice price = motifPassPriceRepository
                .findByArtistSeqAndCountrySeqAndDurationMonths(artistSeq, priceCountrySeq(), months)
                .filter(MotifPassPrice::isActive)
                .orElseThrow(() -> notOnSale(
                        "KR 활성 PASS 가격 없음 artistSeq: " + artistSeq + ", months: " + months));
        if (motifPassRepository.findActivePassExpiry(userSeq, artistSeq).isPresent()) {
            throw new BusinessException(OrderErrorCode.PASS_ALREADY_ACTIVE, "artistSeq: " + artistSeq);
        }
        return new PassOffer(price);
    }

    private static BusinessException notOnSale(String detail) {
        return new BusinessException(OrderErrorCode.PRODUCT_NOT_ON_SALE, detail);
    }

    /** {@code ArtistMotifPassQueryService} 와 같은 조회 — 시드가 없으면 설정 오류다. */
    private Integer priceCountrySeq() {
        return countryRepository.findByCountryCode(PRICE_COUNTRY_CODE)
                .orElseThrow(() -> new IllegalStateException(
                        "기준 국가 행이 없다 — countries 시드(0013-02)가 적용됐는지 확인하라: "
                                + PRICE_COUNTRY_CODE))
                .getCountrySeq();
    }

    /** 팔 수 있는 에셋과 그 KR 활성 가격 — 12.1 이 스냅샷하고 주문명에 아티스트를 쓴다. */
    record AssetOffer(MotifAsset asset, MotifAssetPrice price) {
    }

    /** 팔 수 있는 PASS 의 KR 활성 가격. */
    record PassOffer(MotifPassPrice price) {
    }
}
