package com.ssafy.motif.coreapi.domain.order.docs;

import com.ssafy.motif.common.response.ApiResponse;
import com.ssafy.motif.coreapi.domain.order.dto.OrderCancelResponse;
import com.ssafy.motif.coreapi.domain.order.dto.OrderConfirmRequest;
import com.ssafy.motif.coreapi.domain.order.dto.OrderConfirmResponse;
import com.ssafy.motif.coreapi.domain.order.dto.OrderCreateRequest;
import com.ssafy.motif.coreapi.domain.order.dto.OrderCreateResponse;
import com.ssafy.motif.coreapi.global.docs.FailResponseSchema;
import com.ssafy.motif.coreapi.global.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.UUID;

/**
 * 결제 API 문서 계약 (명세 §12 — 12.1 · 12.2 · 12.3).
 *
 * <p>Swagger 애너테이션만 여기 둔다 — 매핑은 구현 클래스가 갖는다
 * (팀 표준, {@code backend/README.md}).
 *
 * <p>예시 JSON 은 상세 명세 {@code docs/api/detail/12-order.md} 와 <b>같은 값</b>이다.
 */
@Tag(name = "결제 (ORDER)", description = "명세 §12 — 주문 생성 · 결제 승인 · 취소 (토스 승인형, 웹훅 없음)")
public interface OrderControllerDocs {

    String CREATE_DESCRIPTION = """
            결제 흐름의 **1단계**다. 서버가 KR 활성 가격을 읽어 `orders` 에 **스냅샷**하고
            `PENDING` 주문을 만든 뒤, 토스 결제 위젯에 넘길 값을 돌려준다. 다음은 위젯 실행 →
            **§12.2 승인**이다. 토스는 서버로 웹훅을 보내지 않으므로 승인은 클라이언트가
            성공 콜백을 받아 §12.2 를 호출해야 완료된다.

            - **인증 필요.** 주문은 호출자 것이고 보유·패스 판정도 호출자 기준이다.
            - **금액은 서버가 정한다.** 요청에 금액 칸이 없다. 응답 `amount` 를 위젯에 그대로
              넘긴다 — **문자열**이다(`NUMERIC(10,2)`).
            - **유효 시간 30분.** `expiresDttm` 이 지나면 승인이 거절되고 배치가 `EXPIRED` 로 바꾼다.
            - 주문 식별자만 **UUID** 다 — 토스 `orderId` 로 쓰이는 외부 잔존 식별자라 숫자 PK 를
              노출하지 않는다(§1.1).

            ### 두 형태 — `productType` 이 정한다

            | productType | 필수 | 보내지 않는다 |
            |---|---|---|
            | `ASSET` | `assetSeq` | `artistSeq` · `passDurationMonths` |
            | `PASS` | `artistSeq` · `passDurationMonths` | `assetSeq` |

            어긋나면 400 `INVALID_PARAMETER` 이고 `data` 에 필드 오류 배열이 실린다 *(기본안)*.
            반대 형태의 칸을 무시하지 않고 거절한다 — 무시하면 클라이언트 버그가 조용히 통과해
            다른 상품이 결제된다.

            ### orderName *(기본안)*

            `orders` 에 칼럼이 없어 응답에서만 만든다 — 결제창과 영수증에 그대로 찍힌다.
            아티스트명은 `users.nickname` 이다.

            | productType | 형식 | 예 |
            |---|---|---|
            | `ASSET` | `{아티스트명} 모티프 에셋` | 카리나 모티프 에셋 |
            | `PASS` | `{아티스트명} 모티프 패스 {n}개월` | 카리나 모티프 패스 1개월 |

            ### 판정 순서 *(기본안 — 실패표 순서)*

            존재(404) → 판매 가능(409 `PRODUCT_NOT_ON_SALE`) → 보유·패스(409).
            판매 중단(`is_active=FALSE`) 에셋은 §11.2 상세와 달리 **숨기지 않고** 409 다.

            - `PRODUCT_NOT_ON_SALE` — `is_active=FALSE`, 또는 KR 활성 가격이 없다. PASS 는
              **요청한 개월 수**의 활성 가격이 있어야 한다(§11.4 `prices[].durationMonths`).
            - `ALREADY_OWNED` — ACTIVE 개별 구매 보유. 개별 구매와 패스가 둘 다 있으면 이 코드다
              (`OWNED > PASS`).
            - `COVERED_BY_PASS` — 유효 패스로 이미 열린 에셋. **`PREMIUM` 은 패스로 열리지
              않으므로 이 코드가 나오지 않는다.** `SECRET` 은 `NORMAL` 과 같다.
            - `PASS_ALREADY_ACTIVE` — 유효 패스 보유 중. 만료·환불된 패스는 재구매를 막지 않는다.

            유효 패스 판정은 `status='ACTIVE' AND expires_dttm > now()` 를 **DB 시각**으로 한다.

            같은 상품의 `PENDING` 주문이 이미 있어도 막지 않는다 — 시도마다 새 행이다. 위젯에서
            이탈했으면 §12.3 으로 취소하거나 그냥 두면 30분 뒤 `EXPIRED` 다.
            """;

    String REQUEST_ASSET_EXAMPLE = """
            { "productType": "ASSET", "assetSeq": 21 }""";

    String REQUEST_PASS_EXAMPLE = """
            { "productType": "PASS", "artistSeq": 3, "passDurationMonths": 1 }""";

    String CREATE_ASSET_EXAMPLE = """
            {
              "result": "SUCCESS",
              "code": "SUCCESS",
              "message": "요청이 성공했습니다.",
              "data": {
                "orderUuid": "e3b0c442-98fc-4c14-a1b2-3d4e5f678901",
                "orderName": "카리나 모티프 에셋",
                "amount": "3900.00",
                "currency": "KRW",
                "expiresDttm": "2026-09-10T00:30:00.000Z"
              },
              "timestamp": "2026-09-10T00:00:00.000Z"
            }""";

    String CREATE_PASS_EXAMPLE = """
            {
              "result": "SUCCESS",
              "code": "SUCCESS",
              "message": "요청이 성공했습니다.",
              "data": {
                "orderUuid": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
                "orderName": "카리나 모티프 패스 1개월",
                "amount": "9900.00",
                "currency": "KRW",
                "expiresDttm": "2026-09-10T00:30:00.000Z"
              },
              "timestamp": "2026-09-10T00:00:00.000Z"
            }""";

    String SHAPE_FAIL_EXAMPLE = """
            {
              "result": "FAIL",
              "code": "INVALID_PARAMETER",
              "message": "요청 값이 올바르지 않습니다.",
              "data": [
                { "field": "artistSeq", "message": "ASSET 주문에는 보내지 않습니다." }
              ],
              "timestamp": "2026-09-10T00:00:00.000Z"
            }""";

    String NOT_FOUND_EXAMPLE = """
            {
              "result": "FAIL",
              "code": "ASSET_NOT_FOUND",
              "message": "존재하지 않는 에셋입니다.",
              "data": null,
              "timestamp": "2026-09-10T00:00:00.000Z"
            }""";

    String COVERED_BY_PASS_EXAMPLE = """
            {
              "result": "FAIL",
              "code": "COVERED_BY_PASS",
              "message": "패스로 이용 중인 에셋입니다.",
              "data": null,
              "timestamp": "2026-09-10T00:00:00.000Z"
            }""";

    @Operation(summary = "주문 생성 — 가격 스냅샷 (§12.1)", description = CREATE_DESCRIPTION)
    @RequestBody(required = true, description = "productType 에 따라 두 형태 중 하나",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = OrderCreateRequest.class),
                    examples = {
                            @ExampleObject(name = "ASSET — 에셋 구매", value = REQUEST_ASSET_EXAMPLE),
                            @ExampleObject(name = "PASS — 패스 구매", value = REQUEST_PASS_EXAMPLE)
                    }))
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201", description = "주문 생성됨 — data 가 토스 위젯 파라미터",
                    content = @Content(mediaType = "application/json",
                            examples = {
                                    @ExampleObject(name = "ASSET", value = CREATE_ASSET_EXAMPLE),
                                    @ExampleObject(name = "PASS", value = CREATE_PASS_EXAMPLE)
                            })),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "productType 없음 · 형태 불일치 · passDurationMonths 가 양수가 아님 (`INVALID_PARAMETER`)",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = FailResponseSchema.class),
                            examples = @ExampleObject(value = SHAPE_FAIL_EXAMPLE))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "대상 없음 — `ASSET_NOT_FOUND` · `ARTIST_NOT_FOUND`(삭제 포함)",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = FailResponseSchema.class),
                            examples = @ExampleObject(value = NOT_FOUND_EXAMPLE))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "`PRODUCT_NOT_ON_SALE` · `ALREADY_OWNED` · `COVERED_BY_PASS` · `PASS_ALREADY_ACTIVE`",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = FailResponseSchema.class),
                            examples = @ExampleObject(value = COVERED_BY_PASS_EXAMPLE)))
    })
    ApiResponse<OrderCreateResponse> create(
            @Parameter(hidden = true) AuthenticatedUser user,
            OrderCreateRequest request);

    // ---------------------------------------------------------------------------------
    // 12.2 결제 승인
    // ---------------------------------------------------------------------------------

    String CONFIRM_DESCRIPTION = """
            결제 흐름의 **3단계**다. 클라이언트가 토스 위젯의 `successUrl` 콜백 파라미터
            (`paymentKey` · `orderId` · `amount`)를 **그대로** 실어 보내면, 서버가 토스 승인 API 를
            호출하고 같은 트랜잭션에서 권한(`asset_purchases` / `motif_passes`)을 만든다. 토스는
            서버로 웹훅을 보내지 않으므로 **이 호출이 없으면 결제가 완료되지 않는다.**

            - **인증 필요.** 본인 주문만 승인할 수 있다 — 타인 주문은 404.
            - `orderId` 는 경로의 `orderUuid` 와 같아야 한다. 다르면 400 `INVALID_PARAMETER`.
            - `amount` 는 문자열이고 주문 스냅샷과 **`BigDecimal.compareTo`** 로 대조한다 — `3900` 과
              `3900.00` 은 같다. 다르면 토스를 부르지 않고 400 `AMOUNT_MISMATCH`.

            ### 서버 처리 순서

            1. 본인 주문 조회 → 404 `ORDER_NOT_FOUND`
            2. 금액 대조 → 400 `AMOUNT_MISMATCH` (토스 호출 전 차단)
            3. 상태 확인 — `PAID`·`REFUNDED` 는 409 `ORDER_ALREADY_PAID`. 종료 상태(`EXPIRED`·`CANCELED`·
               `FAILED`)와 30분이 지난 `PENDING` 은 409 `ORDER_EXPIRED` *(기본안 — 종료 상태 통칭)*
            4. 판매 가능 재검증 — §12.1 과 같은 판정. 실패하면 주문을 `EXPIRED` 로 바꾸고 409
               (`PRODUCT_NOT_ON_SALE` / `ALREADY_OWNED` / `COVERED_BY_PASS` / `PASS_ALREADY_ACTIVE`)
            5. 조건부 UPDATE `PENDING → PAID` (`status='PENDING' AND expires_dttm > now()`) — 0행이면
               만료·중복 판정
            6. **토스 승인 API 호출** — 거절되면 트랜잭션을 되돌리고 주문을 `FAILED` 로 남긴 뒤
               402 `PAYMENT_FAILED`, `data` 에 `toss: {code}`
            7. 권한 생성 — ASSET 은 `asset_purchases(ACTIVE)`, PASS 는 `motif_passes` 발급 또는
               **같은 행 재활성**(`started=now`, `expires=now+n개월`, 취소 의사 초기화)

            *(기본안)* 5 가 6 보다 앞이다 — 같은 트랜잭션 안이라 커밋 전에는 밖에서 `PAID` 가 보이지
            않고, 행 잠금 덕에 같은 주문의 동시 승인이 직렬화되어 두 번째는 토스를 부르지 않고 409 로
            끝난다. 원본 순서(승인 → UPDATE)로 하면 토스 승인 뒤에 만료·중복이 발견되어 돈만 빠지는
            창이 생긴다. 같은 회원의 승인은 회원 단위 잠금으로 한 줄로 선다 — 같은 상품의 주문 두 개가
            연달아 승인되는 일이 없다.

            ### 결과 불명 · 재시도

            토스가 답하지 않았거나(타임아웃·5xx) 우리 키가 틀렸으면 500 `INTERNAL_ERROR` 이고
            **주문은 `PENDING` 그대로**다. 같은 `paymentKey` 로 다시 부르면 된다 — 이미 승인된 결제면
            토스가 `ALREADY_PROCESSED_PAYMENT` 를 주고, 서버가 결제 조회로 `orderId`·금액·`DONE` 을
            확인한 뒤 승인으로 처리한다.

            **거절된 주문은 되살리지 않는다.** 402 뒤의 재시도는 §12.1 부터 새 주문이다.

            ### 응답

            `productType` 에 따라 `pass` 또는 `asset` 중 **하나만** 실린다 — 다른 쪽 키는 응답에 없다.
            `pass.expiresDttm` 은 새 이용 기간의 만료(승인 시각 + 개월 수, KST 달력 기준)다.
            """;

    String REQUEST_CONFIRM_EXAMPLE = """
            {
              "paymentKey": "toss-payment-key",
              "orderId": "e3b0c442-98fc-4c14-a1b2-3d4e5f678901",
              "amount": "3900.00"
            }""";

    String CONFIRM_PASS_EXAMPLE = """
            {
              "result": "SUCCESS",
              "code": "SUCCESS",
              "message": "요청이 성공했습니다.",
              "data": {
                "orderUuid": "e3b0c442-98fc-4c14-a1b2-3d4e5f678901",
                "status": "PAID",
                "productType": "PASS",
                "pass": { "expiresDttm": "2026-10-02T06:00:00.000Z" }
              },
              "timestamp": "2026-09-10T00:00:00.000Z"
            }""";

    String CONFIRM_ASSET_EXAMPLE = """
            {
              "result": "SUCCESS",
              "code": "SUCCESS",
              "message": "요청이 성공했습니다.",
              "data": {
                "orderUuid": "e3b0c442-98fc-4c14-a1b2-3d4e5f678901",
                "status": "PAID",
                "productType": "ASSET",
                "asset": { "assetSeq": 21 }
              },
              "timestamp": "2026-09-10T00:00:00.000Z"
            }""";

    String AMOUNT_MISMATCH_EXAMPLE = """
            {
              "result": "FAIL",
              "code": "AMOUNT_MISMATCH",
              "message": "결제 금액이 주문과 다릅니다.",
              "data": null,
              "timestamp": "2026-09-10T00:00:00.000Z"
            }""";

    String PAYMENT_FAILED_EXAMPLE = """
            {
              "result": "FAIL",
              "code": "PAYMENT_FAILED",
              "message": "결제 승인에 실패했습니다.",
              "data": "toss: REJECT_CARD_COMPANY",
              "timestamp": "2026-09-10T00:00:00.000Z"
            }""";

    String ORDER_NOT_FOUND_EXAMPLE = """
            {
              "result": "FAIL",
              "code": "ORDER_NOT_FOUND",
              "message": "존재하지 않는 주문입니다.",
              "data": null,
              "timestamp": "2026-09-10T00:00:00.000Z"
            }""";

    String ORDER_EXPIRED_EXAMPLE = """
            {
              "result": "FAIL",
              "code": "ORDER_EXPIRED",
              "message": "만료된 주문입니다.",
              "data": null,
              "timestamp": "2026-09-10T00:00:00.000Z"
            }""";

    @Operation(summary = "결제 승인 — 토스 성공 콜백 후 (§12.2)", description = CONFIRM_DESCRIPTION)
    @RequestBody(required = true, description = "토스 successUrl 콜백 파라미터 그대로",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = OrderConfirmRequest.class),
                    examples = @ExampleObject(value = REQUEST_CONFIRM_EXAMPLE)))
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "승인됨 — productType 에 따라 pass / asset 중 하나",
                    content = @Content(mediaType = "application/json",
                            examples = {
                                    @ExampleObject(name = "PASS", value = CONFIRM_PASS_EXAMPLE),
                                    @ExampleObject(name = "ASSET", value = CONFIRM_ASSET_EXAMPLE)
                            })),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "`AMOUNT_MISMATCH` — 콜백 amount ≠ 주문 스냅샷 (승인 호출 전 차단) · "
                            + "`INVALID_PARAMETER` — 필수값 누락 · orderId ≠ 경로 orderUuid · amount 가 숫자가 아님",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = FailResponseSchema.class),
                            examples = @ExampleObject(value = AMOUNT_MISMATCH_EXAMPLE))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "402",
                    description = "`PAYMENT_FAILED` — 토스 승인 거절. data 에 토스 실패 코드. 재시도는 새 주문",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = FailResponseSchema.class),
                            examples = @ExampleObject(value = PAYMENT_FAILED_EXAMPLE))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "`ORDER_NOT_FOUND` — 주문 없음 · 타인 주문",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = FailResponseSchema.class),
                            examples = @ExampleObject(value = ORDER_NOT_FOUND_EXAMPLE))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "`ORDER_EXPIRED` — 30분 만료 · 종료 상태 주문 · "
                            + "`ORDER_ALREADY_PAID` — 중복 승인 · "
                            + "`PRODUCT_NOT_ON_SALE` / `ALREADY_OWNED` / `COVERED_BY_PASS` / `PASS_ALREADY_ACTIVE` — "
                            + "승인 직전 재검증 실패 (주문은 EXPIRED 로 바뀐다)",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = FailResponseSchema.class),
                            examples = @ExampleObject(value = ORDER_EXPIRED_EXAMPLE)))
    })
    ApiResponse<OrderConfirmResponse> confirm(
            @Parameter(hidden = true) AuthenticatedUser user,
            @Parameter(description = "주문 식별자 — §12.1 응답의 orderUuid",
                    example = "e3b0c442-98fc-4c14-a1b2-3d4e5f678901") UUID orderUuid,
            OrderConfirmRequest request);

    // ---------------------------------------------------------------------------------
    // 12.3 승인 전 취소
    // ---------------------------------------------------------------------------------

    String CANCEL_DESCRIPTION = """
            위젯에서 이탈한 주문을 접는다 — `PENDING → CANCELED`. **본인 주문만** 가능하고 요청 본문은 없다.

            - **인증 필요.**
            - 부르지 않고 방치해도 30분 뒤 배치가 `EXPIRED` 로 바꾼다 — 기능상 차이는 없고 상태 표기만
              다르다.
            - **만료 시각은 보지 않는다** *(기본안)* — 기간이 지났어도 배치 전이라 `PENDING` 이면
              `CANCELED` 가 된다.
            - `PENDING` 이 아닌 주문(`PAID`·`EXPIRED`·`CANCELED`·`FAILED`·`REFUNDED`)은 409
              `ORDER_NOT_CANCELABLE` — 이미 취소된 주문을 다시 취소해도 같다.
            - 승인(§12.2)이 같은 주문을 잠근 채 토스를 부르고 있으면 그 트랜잭션이 끝나기를 기다린 뒤
              판정한다 — 승인이 커밋되면 409, 되돌아가 `PENDING` 으로 남았으면 취소된다. 둘이 동시에
              이기는 길은 없다.

            취소된 주문은 되살리지 않는다 — 다시 사려면 §12.1 부터 새 주문이다. 취소된 주문의 승인
            시도는 409 `ORDER_EXPIRED` 다.
            """;

    String CANCEL_EXAMPLE = """
            {
              "result": "SUCCESS",
              "code": "SUCCESS",
              "message": "요청이 성공했습니다.",
              "data": { "orderUuid": "e3b0c442-98fc-4c14-a1b2-3d4e5f678901", "status": "CANCELED" },
              "timestamp": "2026-09-10T00:00:00.000Z"
            }""";

    String NOT_CANCELABLE_EXAMPLE = """
            {
              "result": "FAIL",
              "code": "ORDER_NOT_CANCELABLE",
              "message": "취소할 수 없는 주문입니다.",
              "data": null,
              "timestamp": "2026-09-10T00:00:00.000Z"
            }""";

    @Operation(summary = "승인 전 취소 — 위젯 이탈 (§12.3)", description = CANCEL_DESCRIPTION)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "취소됨",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = CANCEL_EXAMPLE))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "`ORDER_NOT_FOUND` — 주문 없음 · 타인 주문",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = FailResponseSchema.class),
                            examples = @ExampleObject(value = ORDER_NOT_FOUND_EXAMPLE))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "`ORDER_NOT_CANCELABLE` — PENDING 이 아님 (PAID · EXPIRED · CANCELED · FAILED · REFUNDED)",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = FailResponseSchema.class),
                            examples = @ExampleObject(value = NOT_CANCELABLE_EXAMPLE)))
    })
    ApiResponse<OrderCancelResponse> cancel(
            @Parameter(hidden = true) AuthenticatedUser user,
            @Parameter(description = "주문 식별자 — §12.1 응답의 orderUuid",
                    example = "e3b0c442-98fc-4c14-a1b2-3d4e5f678901") UUID orderUuid);
}
