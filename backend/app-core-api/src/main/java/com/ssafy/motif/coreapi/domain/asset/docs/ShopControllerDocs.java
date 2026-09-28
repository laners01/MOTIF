package com.ssafy.motif.coreapi.domain.asset.docs;

import com.ssafy.motif.common.response.ApiResponse;
import com.ssafy.motif.common.response.ListResponse;
import com.ssafy.motif.coreapi.domain.asset.dto.ShopSectionItem;
import com.ssafy.motif.coreapi.global.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 숍 API 문서 계약 (명세 §11.3).
 *
 * <p>Swagger 애너테이션만 여기 둔다 — 매핑은 구현 클래스가 갖는다
 * (팀 표준, {@code backend/README.md}).
 *
 * <p>예시 JSON 은 상세 명세 {@code docs/api/detail/11-motif.md} 와 <b>같은 값</b>이다.
 */
@Tag(name = "모티프 (MOTIF)", description = "명세 §11 — 에셋 카탈로그·패스·보관함")
public interface ShopControllerDocs {

    String SHOP_DESCRIPTION = """
            **숍 첫 화면을 한 번의 호출로 그린다.** 내가 구독한 아티스트가 섹션이고, 각
            섹션에 그 아티스트의 에셋이 **전량** 들어 있다.

            > v0.8 에서 삭제됐던 §11.3 을 되살렸다. 당시 판단은 "클라가 §4.7 + §11.1 을
            > 조합한다" 였는데, 숍 첫 화면에서 구독 수만큼 §11.1 을 호출하게 되어 왕복이
            > 아티스트 수에 비례한다.

            - **인증 필요.** 구독·패스·구매 판정이 전부 호출자 기준이다.
            - **파라미터가 없고 페이지네이션도 없다.** 봉투는 `ListResponse` 다.
            - 받은 순서 그대로 그리면 된다 — 아티스트는 **구독 최신순**(§4.6·§4.7 과 같은
              기준), 에셋은 **최신순**이다.
            - **가격은 없다.** 가격·구매 가능 여부는 §11.2 상세다.

            ### 섹션 헤더

            `passActive`·`expiresDttm` 은 그 아티스트의 패스 보유 여부다. 미보유면
            `false`/`null` 이고, 판정은 `status='ACTIVE' AND expires_dttm > now` 를 **DB 가**
            한다 — `status` 만 보면 만료 배치가 돌기 전 구간에서 틀린다.

            §4.7 구독 카드의 집계(`totalAssetCount`·`purchasedAssetCount`)는 여기 없다.
            섹션 헤더에 필요한 것은 아티스트 요약과 패스 여부까지다.

            ### 보이는 에셋

            | 대상 | 노출 |
            |---|---|
            | `is_active=TRUE` | 모든 사용자 |
            | `is_active=FALSE` (판매 중단) | **개별 구매 보유자에게만**. 패스만 있으면 보이지 않는다 |

            `myAccess` 에 **구매 여부가 들어 있다** — `OWNED` 가 개별 구매다. 우선순위는
            `OWNED` > `PASS` > `NONE` 이고, **`PREMIUM` 은 패스로 열리지 않는다**.
            `SECRET` 은 `NORMAL` 과 완전히 같다.

            ### 빈 응답

            구독이 0건이면 **빈 배열 200** 이다. 에셋이 0건인 구독 아티스트는 **섹션이
            나오고 `assets` 가 빈 배열**이다 — 섹션을 빼면 "구독했는데 숍에 없는
            아티스트" 가 생겨 §4.7 구독 목록과 어긋난다. 도메인 실패 분기가 없다.
            """;

    String SUCCESS_EXAMPLE = """
            {
              "result": "SUCCESS",
              "code": "SUCCESS",
              "message": "요청이 성공했습니다.",
              "data": {
                "list": [
                  {
                    "artist": {
                      "artistSeq": 3,
                      "name": "카리나",
                      "groupName": "aespa",
                      "profileImageUrl": "https://cdn.motif.io/profiles/3f9c1a2b.jpg"
                    },
                    "passActive": true,
                    "expiresDttm": "2026-10-01T00:00:00.000Z",
                    "assets": [
                      {
                        "assetSeq": 21,
                        "title": "9월 신규 에셋",
                        "thumbnailUrl": "https://cdn.motif.io/assets/2b7f1c93/thumb.jpg",
                        "description": "9월 신규 에셋 소개 문구",
                        "durationMs": 9800,
                        "assetType": "NORMAL",
                        "myAccess": "OWNED"
                      }
                    ]
                  }
                ]
              },
              "timestamp": "2026-09-10T00:00:00.000Z"
            }""";

    @Operation(summary = "숍 전량 — 구독 아티스트 × 에셋 (§11.3)", description = SHOP_DESCRIPTION)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "성공 — 구독이 없으면 빈 배열",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = SUCCESS_EXAMPLE)))
    })
    ApiResponse<ListResponse<ShopSectionItem>> shop(
            @Parameter(hidden = true) AuthenticatedUser user);
}
