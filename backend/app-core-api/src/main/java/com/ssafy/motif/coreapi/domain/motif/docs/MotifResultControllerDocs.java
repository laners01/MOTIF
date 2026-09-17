package com.ssafy.motif.coreapi.domain.motif.docs;

import com.ssafy.motif.common.response.ApiResponse;
import com.ssafy.motif.common.response.CursorResponse;
import com.ssafy.motif.coreapi.domain.motif.dto.MotifResultItem;
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
 * 보관함 API 문서 계약 (명세 §11.5).
 *
 * <p>Swagger 애너테이션만 여기 둔다 — 매핑은 구현 클래스가 갖는다
 * (팀 표준, {@code backend/README.md}).
 *
 * <p>예시 JSON 은 상세 명세 {@code docs/api/detail/11-motif.md} 와 <b>같은 값</b>이다.
 */
@Tag(name = "모티프 (MOTIF)", description = "명세 §11 — 에셋 카탈로그·패스·보관함")
public interface MotifResultControllerDocs {

    String LIST_DESCRIPTION = """
            내가 만든 모티프 결과물 보관함이다. **커서 페이지네이션**(§1.9), **최신순만**
            지원한다 — 정렬 파라미터를 받지 않는다.

            §5.8(아티스트 상세의 "내 모티프 전체 보기")은 **이 API 에 `artistSeq` 를 준
            호출**이다. 별도 API 가 없다.

            - **인증 필요.** 본인 것만 나온다.
            - `cursor` 를 생략하면 최초 호출이다. 빈 문자열도 같다.
            - **역방향은 아직 계약이 아니다** — `prevCursor` 는 `null`, `hasPrevious` 는
              `false` 다.

            ### thumbnailUrl 은 서명된 주소다

            모티프 결과물은 전량 `private/` 프리픽스라 **CloudFront signed URL** 로만
            열린다(§1.7). 서명 만료는 **6시간**이다 — 재생목록(10분)보다 긴 이유는 `<img>`
            가 403 이 되면 되받아 복구할 경로가 없기 때문이다.

            ### 없는 것

            **`viewCount` 가 없다.** 모티프 결과물에는 조회수 카운터가 없다(R2 예외 명시) —
            참여 영상(§7·§8)과 다른 점이고, 값이 비어 있는 것이 아니라 개념이 없다.

            ### 보이는 것

            - 본인 것만. 삭제한 결과물은 제외한다.
            - **삭제된 아티스트의 결과물도 나온다** — 내가 만든 것이고 아티스트 삭제와
              무관하다(§11.1 보유함과 같은 규칙).
            - `isRep` 은 대표 모티프 여부다(§4.1 `repMotif` 와 같은 값). 사용자당 하나뿐이다.

            ### 빈 목록 · 망가진 커서

            결과물이 0건이면 빈 목록 200 이고 `nextCursor` 도 `null` 이다. 도메인 실패
            분기는 없다. 다만 **망가진 커서는 400** 이다 — 조용히 첫 페이지로 되돌리면
            무한 스크롤이 원인 없이 맨 위로 점프한다.
            """;

    String SUCCESS_EXAMPLE = """
            {
              "result": "SUCCESS",
              "code": "SUCCESS",
              "message": "요청이 성공했습니다.",
              "data": {
                "list": [
                  {
                    "resultSeq": 10,
                    "thumbnailUrl": "https://cdn.motif.io/private/motif/9c1d2e3f-4a5b-6c7d-8e9f-0a1b2c3d4e5f/thumb.jpg?Expires=1790000000&Signature=...&Key-Pair-Id=K27QE85H6DLYDD",
                    "artist": {
                      "artistSeq": 3,
                      "name": "카리나",
                      "groupName": "aespa",
                      "profileImageUrl": "https://cdn.motif.io/profiles/3f9c1a2b.jpg"
                    },
                    "regDttm": "2026-09-01T10:00:00.000Z",
                    "isRep": true
                  }
                ],
                "prevCursor": null,
                "nextCursor": "MTA",
                "hasPrevious": false,
                "hasNext": true
              },
              "timestamp": "2026-09-10T00:00:00.000Z"
            }""";

    @Operation(summary = "보관함 목록 (§11.5)", description = LIST_DESCRIPTION)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "성공 — 결과물이 없으면 빈 목록",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = SUCCESS_EXAMPLE))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "망가진 커서",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = FailResponseSchema.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "result": "FAIL",
                                      "code": "INVALID_PARAMETER",
                                      "message": "요청 값이 올바르지 않습니다.",
                                      "data": null,
                                      "timestamp": "2026-09-10T00:00:00.000Z"
                                    }""")))
    })
    ApiResponse<CursorResponse<MotifResultItem>> results(
            @Parameter(hidden = true) AuthenticatedUser user,
            @Parameter(description = "아티스트 식별자 — 생략하면 전체 (§5.8 진입점)", example = "3")
            Integer artistSeq,
            @Parameter(description = "다음 페이지 토큰 — 최초 호출은 생략") String cursor,
            @Parameter(description = "페이지 크기 — 최대 100", example = "20") int size);
}
