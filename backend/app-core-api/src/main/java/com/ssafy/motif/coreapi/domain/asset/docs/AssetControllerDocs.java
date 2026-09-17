package com.ssafy.motif.coreapi.domain.asset.docs;

import com.ssafy.motif.common.response.ApiResponse;
import com.ssafy.motif.common.response.PageResponse;
import com.ssafy.motif.coreapi.domain.asset.dto.AssetCatalogItem;
import com.ssafy.motif.coreapi.global.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 에셋 API 문서 계약 (명세 §11.1).
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
}
