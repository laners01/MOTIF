package com.ssafy.motif.coreapi.domain.order.service;

import com.ssafy.motif.common.error.CommonErrorCode;
import com.ssafy.motif.common.error.FieldErrorResponse;
import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.coreapi.domain.artist.ArtistErrorCode;
import com.ssafy.motif.coreapi.domain.asset.AssetErrorCode;
import com.ssafy.motif.coreapi.domain.asset.dto.MyAccess;
import com.ssafy.motif.coreapi.domain.order.OrderErrorCode;
import com.ssafy.motif.coreapi.domain.order.dto.OrderCreateRequest;
import com.ssafy.motif.coreapi.domain.order.dto.OrderCreateResponse;
import com.ssafy.motif.domain.core.artist.ArtistName;
import com.ssafy.motif.domain.core.artist.ArtistRepository;
import com.ssafy.motif.domain.core.asset.MotifAsset;
import com.ssafy.motif.domain.core.asset.MotifAssetPrice;
import com.ssafy.motif.domain.core.asset.MotifAssetPriceRepository;
import com.ssafy.motif.domain.core.asset.MotifAssetRepository;
import com.ssafy.motif.domain.core.country.CountryRepository;
import com.ssafy.motif.domain.core.order.Order;
import com.ssafy.motif.domain.core.order.OrderRepository;
import com.ssafy.motif.domain.core.pass.MotifPassPrice;
import com.ssafy.motif.domain.core.pass.MotifPassPriceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * 주문 생성 (API 명세 §12.1) — 결제 흐름의 앞단.
 *
 * <p><b>금액은 서버가 정한다.</b> 요청에는 금액 칸이 없고, KR 활성 가격 행을 읽어
 * {@code orders} 에 스냅샷한다. 클라이언트는 응답의 {@code amount} 를 토스 위젯에 그대로
 * 넘기고, 승인(§12.2)이 콜백 금액을 이 스냅샷과 대조한다.
 *
 * <p><b>판정 순서는 실패표 순서다</b> — 존재(404) → 판매 가능(409 {@code PRODUCT_NOT_ON_SALE})
 * → 보유·패스(409). 판매 중단 에셋은 §11.2 상세와 달리 <b>숨기지 않고</b> 409 로 답한다
 * (원본 §12.1 실패표가 {@code is_active=FALSE} 를 409 로 못박았다).
 *
 * <p><b>ASSET 의 보유·패스 판정은 {@link MyAccess#of} 다.</b> §11.1·§11.2·§11.3 이 쓰는 그
 * 규칙({@code OWNED > PASS > NONE}, {@code PREMIUM} 은 패스로 열리지 않는다)을 여기서 다시
 * 쓰면 상세 화면은 {@code PASS} 라는데 주문은 열리는 순간이 생긴다.
 *
 * <p>같은 상품의 {@code PENDING} 주문이 이미 있어도 막지 않는다 — 시도마다 새 행이다
 * (DB §33). 위젯에서 이탈한 뒤 다시 누르는 것이 정상 경로다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class OrderCommandService {

    /**
     * 가격을 고를 국가 (§12.1).
     *
     * <p>국가 협상이 아직 없어 KR 고정이다. {@code AssetQueryService}·
     * {@code ArtistMotifPassQueryService} 에도 같은 값이 있지만 <b>공유하지 않는다</b> —
     * 나라가 늘면 조회는 표시 국가로, 주문은 결제 국가로 따로 정해질 값이다.
     * {@code orders.country_code} 에 이 값이 그대로 스냅샷된다.
     */
    private static final String PRICE_COUNTRY_CODE = "KR";

    /** 주문 유효 시간 (§12.1 — 30분). 지나면 승인이 거절되고 배치가 {@code EXPIRED} 로 바꾼다. */
    static final Duration ORDER_TTL = Duration.ofMinutes(30);

    private final OrderRepository orderRepository;
    private final MotifAssetRepository motifAssetRepository;
    private final MotifAssetPriceRepository motifAssetPriceRepository;
    private final MotifPassPriceRepository motifPassPriceRepository;
    private final ArtistRepository artistRepository;
    private final CountryRepository countryRepository;

    /**
     * §12.1 주문 생성.
     *
     * <p>상품 형태 위반은 400 {@code INVALID_PARAMETER} 이고 {@code data} 에 필드 오류 배열을
     * 싣는다 — {@code @Valid} 실패와 같은 봉투다(README §1.2). 형태가 {@code productType}
     * 값에 따라 갈려 애너테이션 하나로 표현되지 않아 여기서 던진다.
     *
     * @throws BusinessException 400 {@code INVALID_PARAMETER} · 404 {@code ASSET_NOT_FOUND} /
     *                           {@code ARTIST_NOT_FOUND} · 409 {@link OrderErrorCode}
     */
    public OrderCreateResponse create(Integer userSeq, OrderCreateRequest request) {
        List<FieldErrorResponse> shapeErrors = request.shapeErrors();
        if (!shapeErrors.isEmpty()) {
            throw new BusinessException(CommonErrorCode.INVALID_PARAMETER,
                    "productType 형태 불일치: "
                            + shapeErrors.stream().map(FieldErrorResponse::field).toList(),
                    shapeErrors);
        }
        return switch (request.productType()) {
            case ASSET -> createAssetOrder(userSeq, request.assetSeq());
            case PASS -> createPassOrder(userSeq, request.artistSeq(), request.passDurationMonths());
        };
    }

    /**
     * ASSET — 존재 → 판매 중 + KR 활성 가격 → 보유·패스 → 저장.
     *
     * <p>패스 조회는 개별 구매가 없을 때만 한다. {@link MyAccess#of} 가 {@code OWNED} 를
     * 먼저 답하므로 보유자에게 패스를 물을 이유가 없다.
     */
    private OrderCreateResponse createAssetOrder(Integer userSeq, Integer assetSeq) {
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
        boolean passActive = !owned && motifPassPriceRepository
                .findActivePassExpiry(userSeq, asset.getArtistSeq()).isPresent();
        switch (MyAccess.of(owned, passActive, asset.getAssetType())) {
            case OWNED -> throw new BusinessException(
                    OrderErrorCode.ALREADY_OWNED, "assetSeq: " + assetSeq);
            case PASS -> throw new BusinessException(
                    OrderErrorCode.COVERED_BY_PASS, "assetSeq: " + assetSeq);
            case NONE -> { }
        }

        Order order = orderRepository.save(Order.forAsset(
                userSeq, assetSeq, PRICE_COUNTRY_CODE, price.getAmount(), price.getCurrency(),
                Instant.now().plus(ORDER_TTL)));
        return OrderCreateResponse.of(order, artistName(asset.getArtistSeq()) + " 모티프 에셋");
    }

    /**
     * PASS — 존재(비삭제) → 그 개월의 KR 활성 가격 → 유효 패스 없음 → 저장.
     *
     * <p>유효 패스 판정은 {@code MotifPassPriceRepository.findActivePassExpiry} 하나다 —
     * §5.3·§11.4 가 쓰는 것과 같은 메서드라, 구매 화면이 "미보유" 라고 보여 준 사람의 주문이
     * 여기서 막히는 일이 없다. 만료·환불된 패스는 그 조회에 잡히지 않으므로 재구매가 열린다.
     */
    private OrderCreateResponse createPassOrder(Integer userSeq, Integer artistSeq, short months) {
        artistRepository.findByArtistSeqAndDeletedFalse(artistSeq)
                .orElseThrow(() -> new BusinessException(
                        ArtistErrorCode.ARTIST_NOT_FOUND, "artistSeq: " + artistSeq));
        MotifPassPrice price = motifPassPriceRepository
                .findByArtistSeqAndCountrySeqAndDurationMonths(artistSeq, priceCountrySeq(), months)
                .filter(MotifPassPrice::isActive)
                .orElseThrow(() -> notOnSale(
                        "KR 활성 PASS 가격 없음 artistSeq: " + artistSeq + ", months: " + months));
        if (motifPassPriceRepository.findActivePassExpiry(userSeq, artistSeq).isPresent()) {
            throw new BusinessException(OrderErrorCode.PASS_ALREADY_ACTIVE, "artistSeq: " + artistSeq);
        }

        Order order = orderRepository.save(Order.forPass(
                userSeq, artistSeq, months, PRICE_COUNTRY_CODE, price.getAmount(), price.getCurrency(),
                Instant.now().plus(ORDER_TTL)));
        return OrderCreateResponse.of(order,
                artistName(artistSeq) + " 모티프 패스 " + months + "개월");
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

    /**
     * 주문명에 넣는 아티스트 표시 이름 — {@code users.nickname} 이다
     * ({@code ArtistRepository.findNames}, {@code artists} 에는 이름 칸이 없다).
     */
    private String artistName(Integer artistSeq) {
        return artistRepository.findNames(List.of(artistSeq)).stream()
                .findFirst()
                .map(ArtistName::name)
                .orElseThrow(() -> new IllegalStateException(
                        "아티스트의 사용자 행이 없다 artistSeq: " + artistSeq));
    }
}
