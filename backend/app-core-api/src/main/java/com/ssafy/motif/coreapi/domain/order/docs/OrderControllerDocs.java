package com.ssafy.motif.coreapi.domain.order.docs;

import com.ssafy.motif.common.response.ApiResponse;
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

/**
 * 결제 API 문서 계약 (명세 §12 — 12.1).
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
}
