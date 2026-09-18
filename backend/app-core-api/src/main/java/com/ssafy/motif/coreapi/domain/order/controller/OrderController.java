package com.ssafy.motif.coreapi.domain.order.controller;

import com.ssafy.motif.common.annotation.ApiController;
import com.ssafy.motif.common.response.ApiResponse;
import com.ssafy.motif.coreapi.domain.order.docs.OrderControllerDocs;
import com.ssafy.motif.coreapi.domain.order.dto.OrderConfirmRequest;
import com.ssafy.motif.coreapi.domain.order.dto.OrderConfirmResponse;
import com.ssafy.motif.coreapi.domain.order.dto.OrderCreateRequest;
import com.ssafy.motif.coreapi.domain.order.dto.OrderCreateResponse;
import com.ssafy.motif.coreapi.domain.order.service.OrderCommandService;
import com.ssafy.motif.coreapi.domain.order.service.OrderConfirmService;
import com.ssafy.motif.coreapi.global.security.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 결제 API (명세 §12). Base URL 은 {@code /api/v1}.
 *
 * <p><b>전부 인증이 필요하다</b> — 주문은 호출자 것이고 보유·패스 판정도 호출자 기준이다.
 *
 * <p>경로가 {@code /api/v1/**} 이고 {@code /api/v1/admin/**} 이 아니므로 springdoc
 * {@code user} 그룹 문서에 자동으로 잡힌다({@code OpenApiConfig}).
 *
 * <p>Swagger 문서는 {@link OrderControllerDocs} 가 갖는다.
 */
@ApiController
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/orders")
public class OrderController implements OrderControllerDocs {

    private final OrderCommandService orderCommandService;
    private final OrderConfirmService orderConfirmService;

    /**
     * §12.1 주문 생성 — <b>201 Created</b>. 응답이 그대로 토스 위젯 파라미터다.
     *
     * <p>{@code @Valid} 는 {@code productType} 필수·{@code passDurationMonths} 양수만 본다.
     * 형태(ASSET 이면 {@code assetSeq}, PASS 면 {@code artistSeq}·개월)는 서비스가 판정해
     * 같은 400 봉투로 낸다.
     */
    @Override
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<OrderCreateResponse> create(@AuthenticationPrincipal AuthenticatedUser user,
                                                   @Valid @RequestBody OrderCreateRequest request) {
        return ApiResponse.success(orderCommandService.create(user.userSeq(), request));
    }

    /**
     * §12.2 결제 승인 — 200. 토스 콜백 파라미터를 그대로 받는다.
     *
     * <p>경로의 {@code orderUuid} 가 UUID 형식이 아니면 타입 불일치로 400 {@code INVALID_PARAMETER}
     * 다 — 존재하지 않는 주문(404)과 다르다. 토스가 돌려준 {@code orderId} 는 우리가 준 UUID
     * 문자열이라 정상 경로에서는 나올 수 없는 값이다.
     */
    @Override
    @PostMapping("/{orderUuid}/confirm")
    public ApiResponse<OrderConfirmResponse> confirm(@AuthenticationPrincipal AuthenticatedUser user,
                                                     @PathVariable UUID orderUuid,
                                                     @Valid @RequestBody OrderConfirmRequest request) {
        return ApiResponse.success(orderConfirmService.confirm(user.userSeq(), orderUuid, request));
    }
}
