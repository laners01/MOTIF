package com.ssafy.motif.coreapi.domain.order.service;

import com.ssafy.motif.common.error.CommonErrorCode;
import com.ssafy.motif.common.error.FieldErrorResponse;
import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.coreapi.domain.order.OrderErrorCode;
import com.ssafy.motif.coreapi.domain.order.dto.OrderCreateRequest;
import com.ssafy.motif.coreapi.domain.order.dto.OrderCreateResponse;
import com.ssafy.motif.domain.core.artist.ArtistName;
import com.ssafy.motif.domain.core.artist.ArtistRepository;
import com.ssafy.motif.domain.core.order.Order;
import com.ssafy.motif.domain.core.order.OrderRepository;
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
 * <p><b>팔 수 있는가는 {@link ProductAvailability} 가 답한다</b> — 승인 직전 재검증(§12.2)과
 * 같은 판정이다. 순서·코드는 그쪽 javadoc 에 있다.
 *
 * <p>같은 상품의 {@code PENDING} 주문이 이미 있어도 막지 않는다 — 시도마다 새 행이다
 * (DB §33). 위젯에서 이탈한 뒤 다시 누르는 것이 정상 경로다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class OrderCommandService {

    /** 주문 유효 시간 (§12.1 — 30분). 지나면 승인이 거절되고 배치가 {@code EXPIRED} 로 바꾼다. */
    static final Duration ORDER_TTL = Duration.ofMinutes(30);

    private final OrderRepository orderRepository;
    private final ProductAvailability productAvailability;
    private final ArtistRepository artistRepository;

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

    private OrderCreateResponse createAssetOrder(Integer userSeq, Integer assetSeq) {
        ProductAvailability.AssetOffer offer = productAvailability.assetOnSale(userSeq, assetSeq);

        Order order = orderRepository.save(Order.forAsset(
                userSeq, assetSeq, productAvailability.priceCountryCode(),
                offer.price().getAmount(), offer.price().getCurrency(),
                Instant.now().plus(ORDER_TTL)));
        return OrderCreateResponse.of(order, artistName(offer.asset().getArtistSeq()) + " 모티프 에셋");
    }

    private OrderCreateResponse createPassOrder(Integer userSeq, Integer artistSeq, short months) {
        ProductAvailability.PassOffer offer = productAvailability.passOnSale(userSeq, artistSeq, months);

        Order order = orderRepository.save(Order.forPass(
                userSeq, artistSeq, months, productAvailability.priceCountryCode(),
                offer.price().getAmount(), offer.price().getCurrency(),
                Instant.now().plus(ORDER_TTL)));
        return OrderCreateResponse.of(order,
                artistName(artistSeq) + " 모티프 패스 " + months + "개월");
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
