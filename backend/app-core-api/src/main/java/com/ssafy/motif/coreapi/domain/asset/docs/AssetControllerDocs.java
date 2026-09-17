package com.ssafy.motif.coreapi.domain.asset.docs;

import com.ssafy.motif.common.response.ApiResponse;
import com.ssafy.motif.common.response.ListResponse;
import com.ssafy.motif.coreapi.domain.asset.dto.AssetCatalogItem;
import com.ssafy.motif.coreapi.domain.asset.dto.AssetDetailResponse;
import com.ssafy.motif.coreapi.global.docs.FailResponseSchema;
import com.ssafy.motif.coreapi.global.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 에셋 API 문서 계약 (명세 §11.1 · §11.2).
 *
 * <p>Swagger 애너테이션만 여기 둔다 — 매핑은 구현 클래스가 갖는다
 * (팀 표준, {@code backend/README.md}).
 *
 * <p>예시 JSON 은 상세 명세 {@code docs/api/detail/11-motif.md} 와 <b>같은 값</b>이다.
 */
@Tag(name = "모티프 (MOTIF)", description = "명세 §11 — 에셋 카탈로그·패스·보관함")
public interface AssetControllerDocs {

    String LIST_DESCRIPTION = """
            **내가 구매한 에셋**을 전량 준다 — 보유함이다. `artistSeq` 를 주면 그
            아티스트에게서 산 것만 본다.

            > **계약이 바뀌었다** (S15P21D104-219). 이 API 는 원래 "구독 아티스트의 에셋
            > 전량" 이었고 숍 첫 화면을 그리는 자리였다. 그 자리는 **§11.3
            > `GET /api/v1/motif/shop`** 이 가져갔다. 함께 바뀐 것:
            > **페이지네이션이 없어졌고**(`page`·`size` 를 받지 않는다) 봉투가
            > `PageResponse` → `ListResponse` 이며, 항목에서 `myAccess` 가 빠졌다.

            - **인증 필요.** 대상이 "내가 산 것" 이다.
            - **비페이지 전량.** 정렬은 **구매 최신순** 고정이고 파라미터를 받지 않는다.
            - **가격이 없다.** 가격·구매 가능 여부는 §11.2 상세다.
            - **`myAccess` 가 없다.** 전부 개별 구매라 값이 하나뿐인 칸이 된다.

            ### 대상 — 산 것은 산 것이다

            `asset_purchases.status='ACTIVE'` 인 내 구매만 나온다. 세 가지를 **보지 않는다**.

            | 보지 않는 것 | 이유 |
            |---|---|
            | `is_active` (판매 중단) | 판매가 끊겨도 내 보유는 남는다 |
            | 구독 | 구독을 끊어도 산 것은 남는다 |
            | 아티스트 삭제 | 같은 이유. 이름·프로필은 계속 나온다 |

            **패스로만 열리는 에셋은 나오지 않는다** — 산 것이 아니다. 패스로 무엇이
            열리는지는 §11.3 숍의 `myAccess` 가 답한다.

            ### 빈 목록

            구매가 0건이거나 없는 `artistSeq` 를 넘기면 **빈 목록 200** 이다.
            도메인 실패 분기가 없다.
            """;

    String SUCCESS_EXAMPLE = """
            {
              "result": "SUCCESS",
              "code": "SUCCESS",
              "message": "요청이 성공했습니다.",
              "data": {
                "list": [
                  {
                    "assetSeq": 21,
                    "artist": {
                      "artistSeq": 3,
                      "name": "카리나",
                      "groupName": "aespa",
                      "profileImageUrl": "https://cdn.motif.io/profiles/3f9c1a2b.jpg"
                    },
                    "title": "9월 신규 에셋",
                    "thumbnailUrl": "https://cdn.motif.io/assets/2b7f1c93/thumb.jpg",
                    "description": "9월 신규 에셋 소개 문구",
                    "durationMs": 9800,
                    "assetType": "NORMAL"
                  }
                ]
              },
              "timestamp": "2026-09-10T00:00:00.000Z"
            }""";

    @Operation(summary = "내 보유함 — 구매한 에셋 (§11.1)", description = LIST_DESCRIPTION)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "성공 — 구매가 없으면 빈 목록",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = SUCCESS_EXAMPLE)))
    })
    ApiResponse<ListResponse<AssetCatalogItem>> assets(
            @Parameter(hidden = true) AuthenticatedUser user,
            @Parameter(description = "아티스트 식별자 — 생략하면 전체", example = "3")
            Integer artistSeq);

    String DETAIL_DESCRIPTION = """
            구매 화면의 데이터다. 목록(§11.1)이 주지 않는 세 가지가 여기 있다 —
            **미리보기 영상 · 가격 · 구매 가능 여부**.

            - **인증 필요.** `myAccess` 와 노출 판정이 호출자 기준이다.
            - **구독을 보지 않는다.** 아티스트 상세(§5)·딥링크로 들어오는 자리라 미구독이
              정상 경로다.

            ### previewVideoUrl

            **공개 CDN 의 HLS 마스터 재생목록**이다(§1.7). 에셋 미리보기는 `assets/`
            프리픽스라 공개 등급이고 조각도 무서명이라, 보호 등급 재생목록 주소
            (`/api/v1/media/…`)를 쓰지 않는다.

            가공이 끝나기 전에는 **업로드 MP4 키가 그대로** 들어 있고 썸네일은 404 다 —
            어드민 등록 직후의 짧은 구간이다.

            ### price · purchasable

            `price` 는 **KR 활성 가격**이다. 없으면 `null` 이고 `purchasable` 도 `false` 다.
            `price.amount` 는 **문자열**이다 — `NUMERIC(10,2)` 를 부동소수로 보내지 않는다.

            `purchasable` 은 아래를 모두 만족할 때만 `true` 다 *(기본안)*.

            | 조건 | 이유 |
            |---|---|
            | 판매 중(`is_active=TRUE`) | 내려간 상품은 살 수 없다 |
            | KR 활성 가격 존재 | 값이 없으면 결제를 시작할 수 없다 |
            | `myAccess != OWNED` | 이미 산 것은 다시 못 산다 — DB 가 (회원, 에셋) ACTIVE 구매를 하나로 막는다 |

            **`myAccess=PASS` 는 `true` 다.** 패스는 만료되므로 지금 열려 있어도 영구
            보유를 따로 사는 선택이 남아 있어야 한다.

            ### 안 보이는 에셋은 전부 404

            판매 중단(`is_active=FALSE`) 에셋은 **개별 구매자만** 조회할 수 있다. 패스만
            보유한 사용자도 404 다 — 목록(§11.1)의 노출 규칙과 같은 판정이다. 403 을 주면
            "그 에셋은 존재한다" 가 응답에서 읽히므로 미존재와 같은 코드를 쓴다(§1.8).
            """;

    String DETAIL_SUCCESS_EXAMPLE = """
            {
              "result": "SUCCESS",
              "code": "SUCCESS",
              "message": "요청이 성공했습니다.",
              "data": {
                "assetSeq": 21,
                "artist": {
                  "artistSeq": 3,
                  "name": "카리나",
                  "groupName": "aespa",
                  "profileImageUrl": "https://cdn.motif.io/profiles/3f9c1a2b.jpg"
                },
                "title": "9월 신규 에셋",
                "previewVideoUrl": "https://cdn.motif.io/assets/2b7f1c93/master.m3u8",
                "thumbnailUrl": "https://cdn.motif.io/assets/2b7f1c93/thumb.jpg",
                "description": "9월 신규 에셋 소개 문구",
                "durationMs": 9800,
                "assetType": "NORMAL",
                "price": { "amount": "3900.00", "currency": "KRW" },
                "purchasable": true,
                "myAccess": "NONE"
              },
              "timestamp": "2026-09-10T00:00:00.000Z"
            }""";

    @Operation(summary = "에셋 상세 (§11.2)", description = DETAIL_DESCRIPTION)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "성공",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = DETAIL_SUCCESS_EXAMPLE))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "미존재 · 판매 중단 에셋에 대한 비구매자 조회",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = FailResponseSchema.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "result": "FAIL",
                                      "code": "ASSET_NOT_FOUND",
                                      "message": "존재하지 않는 에셋입니다.",
                                      "data": null,
                                      "timestamp": "2026-09-10T00:00:00.000Z"
                                    }""")))
    })
    ApiResponse<AssetDetailResponse> detail(
            @Parameter(hidden = true) AuthenticatedUser user,
            @Parameter(description = "에셋 식별자", example = "21") Integer assetSeq);
}
