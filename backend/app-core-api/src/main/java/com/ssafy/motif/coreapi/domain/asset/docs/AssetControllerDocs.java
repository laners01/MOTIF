package com.ssafy.motif.coreapi.domain.asset.docs;

import com.ssafy.motif.common.response.ApiResponse;
import com.ssafy.motif.common.response.PageResponse;
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
            **쿼리 없이 부르면 내가 구독한 아티스트들의 에셋**이 한 목록으로 온다.
            `artistSeq` 를 주면 구독 여부와 무관하게 그 아티스트의 에셋만 본다.

            > 원본 명세 §11.1 은 "생략하면 전체 아티스트"였다. 숍의 기본 화면이 구독
            > 기반이라 기본 동작을 바꿨다(S15P21D104-224). **전체 카탈로그를 한 번에 주는
            > 경로는 없다** — 미구독 아티스트의 에셋은 아티스트 상세(§5)에서 `artistSeq`
            > 로 들어온다.

            - **인증 필요.** 구독 판정과 `myAccess` 가 호출자 기준이다.
            - 최신순 고정이다. 정렬 파라미터를 받지 않는다.
            - 기본 모드에서는 여러 아티스트가 **섞여서** 최신순으로 온다. 섹션 묶기는
              항목의 `artist.artistSeq` 로 클라이언트가 한다.
            - **목록에 가격이 없다.** 가격·구매 가능 여부는 §11.2 상세다.

            ### 보이는 에셋

            | 대상 | 노출 |
            |---|---|
            | `is_active=TRUE` | 모든 사용자 |
            | `is_active=FALSE` (판매 중단) | **개별 구매 보유자에게만**. 패스만 있으면 보이지 않는다 |

            기본 모드에서는 **삭제된 아티스트**의 에셋이 빠진다 — 구독 행은 아티스트가
            삭제돼도 남기 때문에 거르지 않으면 눌러도 404 인 카드가 섞인다.

            ### myAccess — 우선순위 `OWNED` > `PASS` > `NONE`

            | 값 | 조건 |
            |---|---|
            | `OWNED` | 개별 구매 보유 (`asset_purchases.status='ACTIVE'`) |
            | `PASS` | 그 아티스트의 유효 패스로 열린다 |
            | `NONE` | 권한 없음 |

            개별 구매와 패스가 동시에 유효하면 `OWNED` 다. **`PREMIUM` 은 패스로 열리지
            않는다** — 유효 패스가 있어도 `NONE` 이다. `SECRET` 은 `NORMAL` 과 완전히 같아
            패스가 커버한다.

            ### 빈 목록

            구독이 0건이거나, 없는 `artistSeq` 를 넘기거나, 보여 줄 에셋이 없으면
            **빈 목록 200** 이다. 도메인 실패 분기가 없다.
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
                    "thumbnailUrl": "https://cdn.motif.io/assets/2b7f1c93.jpg",
                    "description": "9월 신규 에셋 소개 문구",
                    "durationMs": 9800,
                    "assetType": "NORMAL",
                    "myAccess": "PASS"
                  }
                ],
                "page": 0,
                "size": 20,
                "totalElements": 42,
                "totalPages": 3,
                "hasNext": true,
                "hasPrevious": false
              },
              "timestamp": "2026-09-10T00:00:00.000Z"
            }""";

    @Operation(summary = "에셋 목록 — 기본 구독 아티스트 (§11.1)", description = LIST_DESCRIPTION)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "성공 — 대상이 없으면 빈 목록",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = SUCCESS_EXAMPLE)))
    })
    ApiResponse<PageResponse<AssetCatalogItem>> assets(
            @Parameter(hidden = true) AuthenticatedUser user,
            @Parameter(description = "아티스트 식별자 — 생략하면 내 구독 아티스트 전체", example = "3")
            Integer artistSeq,
            @Parameter(description = "0-base 페이지", example = "0") int page,
            @Parameter(description = "페이지 크기 — 최대 100", example = "20") int size);

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
