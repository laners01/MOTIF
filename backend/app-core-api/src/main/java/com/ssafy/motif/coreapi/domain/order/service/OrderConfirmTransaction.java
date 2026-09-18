package com.ssafy.motif.coreapi.domain.order.service;

import com.ssafy.motif.common.error.CommonErrorCode;
import com.ssafy.motif.common.error.ErrorCode;
import com.ssafy.motif.common.error.FieldErrorResponse;
import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.coreapi.domain.order.OrderErrorCode;
import com.ssafy.motif.coreapi.domain.order.dto.OrderConfirmRequest;
import com.ssafy.motif.coreapi.domain.order.dto.OrderConfirmResponse;
import com.ssafy.motif.coreapi.domain.order.payment.TossApiException;
import com.ssafy.motif.coreapi.domain.order.payment.TossPayment;
import com.ssafy.motif.coreapi.domain.order.payment.TossPaymentClient;
import com.ssafy.motif.domain.core.order.Order;
import com.ssafy.motif.domain.core.order.OrderRepository;
import com.ssafy.motif.domain.core.order.OrderStatus;
import com.ssafy.motif.domain.core.pass.MotifPass;
import com.ssafy.motif.domain.core.pass.MotifPassRepository;
import com.ssafy.motif.domain.core.purchase.AssetPurchase;
import com.ssafy.motif.domain.core.purchase.AssetPurchaseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 결제 승인의 <b>트랜잭션 본체</b> (API 명세 §12.2 서버 처리 1~5단계).
 *
 * <p>{@link OrderConfirmService} 가 아니라 이 빈에 {@code @Transactional} 이 있는 이유 —
 * 토스가 거절하면 <b>PAID 전이를 되돌린 뒤</b> {@code FAILED} 를 새 트랜잭션으로 기록해야
 * 하는데, 같은 빈 안에서는 프록시를 타지 않아 두 트랜잭션을 가를 수 없다. 파사드가
 * {@link #confirm} 의 롤백을 기다린 뒤 {@link #recordRejection} 을 부른다.
 *
 * <p><b>순서가 계약이다.</b>
 * <ol>
 *   <li>회원 단위 잠금 → 본인 주문 조회 (404)</li>
 *   <li>금액 대조 — 토스 호출 전 (400 {@code AMOUNT_MISMATCH})</li>
 *   <li>상태 — 이미 결제됨(409 {@code ORDER_ALREADY_PAID}) · 종료됨·만료됨(409 {@code ORDER_EXPIRED})</li>
 *   <li>판매 가능 재검증 — 실패면 {@code EXPIRED} 로 바꾸고 409 ({@link ProductAvailability})</li>
 *   <li><b>조건부 UPDATE {@code PENDING → PAID}</b> — 0행이면 만료·중복</li>
 *   <li>토스 승인 호출</li>
 *   <li>권한 생성 — 같은 트랜잭션</li>
 * </ol>
 *
 * <p><b>5 가 6 보다 앞이다(원본은 반대).</b> 같은 트랜잭션 안이라 커밋 전에는 밖에서 PAID 가
 * 보이지 않고, UPDATE 가 잡은 행 잠금과 회원 잠금 덕에 동시 승인이 직렬화되어 두 번째는 토스를
 * 부르지 않고 409 로 끝난다. 원본 순서(승인 → UPDATE)로 하면 토스 승인 <b>뒤에</b> 만료·중복이
 * 드러나 돈은 빠졌는데 권한이 없는 창이 생긴다. 토스 거절이면 트랜잭션이 통째로 되돌아가
 * PAID 도 사라진다.
 *
 * <p><b>거절의 두 종류를 예외 타입으로 가른다.</b> 만료·판매 중단은 {@link OrderRejectedException}
 * — {@code EXPIRED} 전이가 남아야 하므로 {@code noRollbackFor} 다. 토스 거절은
 * {@link PaymentRejectedException} — 롤백이다. 그 밖(토스 결과 불명 · 예상 못 한 예외)도 롤백이며
 * 주문은 {@code PENDING} 에 남아 재시도가 열린다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderConfirmTransaction {

    /** {@code orders.payment_provider} — DB §33 은 현재 이 값만 허용한다. */
    static final String PAYMENT_PROVIDER = "TOSS";

    /**
     * PASS 개월 계산의 시간대 — 판매 국가(KR)다.
     *
     * <p>"n개월 뒤"는 달력 연산이라 시간대가 필요하다. UTC 로 세면 월말 보정이 KST 와 하루
     * 어긋나는 날이 있다(3월 31일 08:00 KST 는 UTC 로 3월 30일이라 +1개월이 4월 30일이 된다).
     */
    static final ZoneId PASS_ZONE = ZoneId.of("Asia/Seoul");

    /** {@code orders.fail_reason VARCHAR(200)}. */
    private static final int FAIL_REASON_MAX = 200;

    private final OrderRepository orderRepository;
    private final AssetPurchaseRepository assetPurchaseRepository;
    private final MotifPassRepository motifPassRepository;
    private final ProductAvailability productAvailability;
    private final TossPaymentClient tossPaymentClient;

    /**
     * §12.2 승인 — 성공하면 권한까지 만들어 커밋한다.
     *
     * @throws BusinessException        400 · 404 · 409 (아래 {@link OrderRejectedException} 포함)
     * @throws OrderRejectedException   만료·판매 중단 — {@code EXPIRED} 전이를 남기고 409
     * @throws PaymentRejectedException 토스 확정 거절 — 롤백. 파사드가 {@code FAILED} 를 기록한다
     */
    @Transactional(noRollbackFor = OrderRejectedException.class)
    public OrderConfirmResponse confirm(Integer userSeq, UUID orderUuid, OrderConfirmRequest request) {
        orderRepository.lockUserOrders(userSeq);
        Order order = orderRepository.findByOrderUuidAndUserSeq(orderUuid, userSeq)
                .orElseThrow(() -> new BusinessException(
                        OrderErrorCode.ORDER_NOT_FOUND, "orderUuid: " + orderUuid));

        BigDecimal amount = matchedAmount(order, request);
        requirePending(order);
        revalidate(order, userSeq);
        markPaidOrReject(order, request.paymentKey());
        approveAtToss(order, request.paymentKey(), amount);
        return grant(order, userSeq);
    }

    /**
     * 토스 거절을 {@code FAILED} 로 남긴다 — {@link #confirm} 의 롤백이 <b>끝난 뒤</b> 새
     * 트랜잭션으로 불러야 한다. 같은 트랜잭션 안에서 부르면 롤백과 함께 사라진다.
     */
    @Transactional
    public void recordRejection(PaymentRejectedException rejection) {
        String reason = abbreviate("toss " + rejection.getCode() + ": " + rejection.getTossMessage());
        int updated = orderRepository.markFailed(
                rejection.getOrderSeq(), PAYMENT_PROVIDER, rejection.getPaymentKey(), reason);
        if (updated == 0) {
            // 이 사이 다른 전이가 있었다 — 배치 만료 등. 거절 사실은 로그로만 남는다
            log.warn("토스 거절을 FAILED 로 기록하지 못함 — 이미 PENDING 이 아님 orderSeq={} code={}",
                    rejection.getOrderSeq(), rejection.getCode());
        }
    }

    // --- 1. 금액 -------------------------------------------------------------------

    /**
     * 콜백 {@code orderId}·{@code amount} 를 주문과 대조한다 — <b>토스를 부르기 전</b>이다.
     *
     * <p>{@code compareTo} 여야 한다. 문자열이나 {@code equals} 로 비교하면 {@code 3900} 과
     * {@code 3900.00} 이 다르게 판정된다(§12.2 — 컷 불가).
     */
    private BigDecimal matchedAmount(Order order, OrderConfirmRequest request) {
        if (!request.pointsTo(order.getOrderUuid())) {
            throw invalidParameter("orderId", "경로의 orderUuid 와 다릅니다.");
        }
        BigDecimal amount = request.amountValue()
                .orElseThrow(() -> invalidParameter("amount", "숫자가 아닙니다."));
        if (amount.compareTo(order.getAmount()) != 0) {
            throw new BusinessException(OrderErrorCode.AMOUNT_MISMATCH,
                    "orderUuid: " + order.getOrderUuid() + ", amount " + amount + " ≠ " + order.getAmount());
        }
        return amount;
    }

    private static BusinessException invalidParameter(String field, String message) {
        return new BusinessException(CommonErrorCode.INVALID_PARAMETER, field + ": " + message,
                List.of(new FieldErrorResponse(field, message)));
    }

    // --- 2. 상태 -------------------------------------------------------------------

    /**
     * {@code PENDING} 이고 아직 만료 전이어야 한다.
     *
     * <p>종료 상태({@code EXPIRED}·{@code CANCELED}·{@code FAILED})는 전부 {@code ORDER_EXPIRED}
     * 다(기본안 — §15 에 더 맞는 코드가 없어 새 코드를 만들지 않는다). 기간이 지난 {@code PENDING}
     * 은 여기서 {@code EXPIRED} 로 바꾼다 — 배치를 기다릴 이유가 없다. 이 앱 시각 판정은 앞단
     * 정리일 뿐이고, 최종 만료 판정은 5단계 조건부 UPDATE 의 DB 시각이다.
     */
    private void requirePending(Order order) {
        switch (order.getStatus()) {
            case PAID, REFUNDED -> throw new BusinessException(
                    OrderErrorCode.ORDER_ALREADY_PAID, "orderUuid: " + order.getOrderUuid());
            case EXPIRED, CANCELED, FAILED -> throw new BusinessException(
                    OrderErrorCode.ORDER_EXPIRED,
                    "orderUuid: " + order.getOrderUuid() + ", status: " + order.getStatus());
            case PENDING -> {
                if (!order.getExpiresDttm().isAfter(Instant.now())) {
                    throw expire(order, OrderErrorCode.ORDER_EXPIRED, "expiresDttm: " + order.getExpiresDttm());
                }
            }
        }
    }

    /** {@code PENDING → EXPIRED} 로 바꾸고 던진다 — 커밋되어야 하므로 {@link OrderRejectedException} 이다. */
    private OrderRejectedException expire(Order order, ErrorCode code, String detail) {
        orderRepository.markExpired(order.getOrderSeq());
        return new OrderRejectedException(code, "orderUuid: " + order.getOrderUuid() + " — " + detail);
    }

    // --- 3. 재검증 -----------------------------------------------------------------

    /**
     * 주문을 만들 때 통과한 판정을 다시 한다 — 사이에 판매 중단·가격 비활성·다른 주문의 승인이
     * 있었을 수 있다. 실패면 {@code EXPIRED} 로 바꾸고 그 코드로 409 다.
     *
     * <p>존재 코드(404 — 아티스트 삭제 등)는 여기서 {@code PRODUCT_NOT_ON_SALE} 이다. 주문은
     * 있는데 상품이 사라진 것이라 "없다"보다 "안 판다"가 맞다.
     */
    private void revalidate(Order order, Integer userSeq) {
        try {
            switch (order.getProductType()) {
                case ASSET -> productAvailability.assetOnSale(userSeq, order.getAssetSeq());
                case PASS -> productAvailability.passOnSale(
                        userSeq, order.getArtistSeq(), order.getPassDurationMonths());
            }
        } catch (BusinessException e) {
            ErrorCode code = e.getErrorCode().getStatus() == 404
                    ? OrderErrorCode.PRODUCT_NOT_ON_SALE
                    : e.getErrorCode();
            throw expire(order, code, e.getMessage());
        }
    }

    // --- 4. PAID 전이 --------------------------------------------------------------

    /**
     * 조건부 UPDATE — 0행이면 다시 읽어 중복({@code ORDER_ALREADY_PAID})과 만료
     * ({@code ORDER_EXPIRED})를 가른다.
     *
     * <p>회원 잠금 뒤에 {@code PENDING} 을 읽었으므로 여기서 0행이 나는 길은 DB 시각 기준 만료가
     * 거의 전부지만, 판정은 값을 다시 읽어 한다 — 추측으로 코드를 고르지 않는다.
     */
    private void markPaidOrReject(Order order, String paymentKey) {
        if (orderRepository.markPaid(order.getOrderSeq(), PAYMENT_PROVIDER, paymentKey) == 1) {
            return;
        }
        OrderStatus current = orderRepository.findById(order.getOrderSeq())
                .map(Order::getStatus)
                .orElse(order.getStatus());
        if (current == OrderStatus.PAID || current == OrderStatus.REFUNDED) {
            throw new BusinessException(OrderErrorCode.ORDER_ALREADY_PAID,
                    "orderUuid: " + order.getOrderUuid() + " (조건부 UPDATE 0행)");
        }
        throw expire(order, OrderErrorCode.ORDER_EXPIRED, "조건부 UPDATE 0행, status: " + current);
    }

    // --- 5. 토스 -------------------------------------------------------------------

    /**
     * 토스 승인. 거절이면 {@link PaymentRejectedException} — 롤백된다.
     *
     * <p>{@code ALREADY_PROCESSED_PAYMENT} 는 거절이 아니라 "이미 승인됐다"다 — 앞선 시도가
     * 승인 뒤 끊겨 우리 주문만 {@code PENDING} 으로 남은 경우다. 결제 조회로 이 주문·이 금액의
     * {@code DONE} 인지 확인한 뒤 승인으로 간주한다. 이 경로가 없으면 돈은 빠졌는데 402 가 나간다.
     *
     * <p>결과 불명({@code TossUnavailableException})은 여기서 잡지 않는다 — 그대로 올라가 롤백되고
     * 500 이며, 주문은 {@code PENDING} 에 남는다.
     */
    private TossPayment approveAtToss(Order order, String paymentKey, BigDecimal amount) {
        String orderId = order.getOrderUuid().toString();
        try {
            TossPayment payment = tossPaymentClient.confirm(paymentKey, orderId, amount);
            if (!payment.isDone()) {
                throw new PaymentRejectedException(order.getOrderSeq(), paymentKey,
                        "STATUS_" + payment.status(), "승인 응답 status 가 DONE 이 아니다");
            }
            return payment;
        } catch (TossApiException e) {
            if (TossApiException.ALREADY_PROCESSED_PAYMENT.equals(e.getCode())) {
                return recoverAlreadyProcessed(order, paymentKey, amount, e);
            }
            throw new PaymentRejectedException(order.getOrderSeq(), paymentKey, e.getCode(), e.getTossMessage());
        }
    }

    private TossPayment recoverAlreadyProcessed(Order order, String paymentKey, BigDecimal amount,
                                                TossApiException original) {
        TossPayment payment = tossPaymentClient.find(paymentKey);
        boolean sameOrder = order.getOrderUuid().toString().equals(payment.orderId());
        boolean sameAmount = payment.totalAmount() != null && payment.totalAmount().compareTo(amount) == 0;
        if (sameOrder && sameAmount && payment.isDone()) {
            log.info("토스 ALREADY_PROCESSED_PAYMENT — 결제 조회로 확인해 승인으로 간주 orderUuid={}",
                    order.getOrderUuid());
            return payment;
        }
        throw new PaymentRejectedException(order.getOrderSeq(), paymentKey, original.getCode(),
                original.getTossMessage() + " (조회 불일치 orderId=" + payment.orderId()
                        + ", status=" + payment.status() + ", totalAmount=" + payment.totalAmount() + ")");
    }

    // --- 6. 권한 -------------------------------------------------------------------

    /**
     * ASSET 은 보유권 INSERT, PASS 는 발급 또는 <b>같은 행 재활성</b>(DB §35).
     *
     * <p>재검증(3단계)이 회원 잠금 아래에서 끝났으므로 여기서 {@code uq_asset_purchases_active}·
     * {@code uq_motif_passes_user_artist} 에 걸릴 길은 없다 — 걸리면 그것이 버그다.
     */
    private OrderConfirmResponse grant(Order order, Integer userSeq) {
        return switch (order.getProductType()) {
            case ASSET -> {
                assetPurchaseRepository.save(
                        AssetPurchase.grant(userSeq, order.getAssetSeq(), order.getOrderSeq()));
                yield OrderConfirmResponse.forAsset(order.getOrderUuid(), order.getAssetSeq());
            }
            case PASS -> {
                short months = order.getPassDurationMonths();
                Instant started = Instant.now();
                Instant expires = ZonedDateTime.ofInstant(started, PASS_ZONE).plusMonths(months).toInstant();
                MotifPass pass = motifPassRepository
                        .findByUserSeqAndArtistSeq(userSeq, order.getArtistSeq())
                        .map(existing -> {
                            existing.reactivate(order.getOrderSeq(), months, started, expires);
                            return existing;
                        })
                        .orElseGet(() -> motifPassRepository.save(MotifPass.issue(
                                userSeq, order.getArtistSeq(), order.getOrderSeq(), months, started, expires)));
                yield OrderConfirmResponse.forPass(order.getOrderUuid(), pass.getExpiresDttm());
            }
        };
    }

    private static String abbreviate(String reason) {
        return reason.length() <= FAIL_REASON_MAX ? reason : reason.substring(0, FAIL_REASON_MAX);
    }
}
