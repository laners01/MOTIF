package com.ssafy.motif.coreapi.domain.motif.docs;

import com.ssafy.motif.common.response.ApiResponse;
import com.ssafy.motif.common.response.CursorResponse;
import com.ssafy.motif.coreapi.domain.motif.dto.MotifResultDetailResponse;
import com.ssafy.motif.coreapi.domain.motif.dto.MotifResultItem;
import com.ssafy.motif.coreapi.domain.motif.dto.MotifResultUpdateRequest;
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
 * 보관함 API 문서 계약 (명세 §11.5 · §11.6 · §11.8).
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

    String DETAIL_DESCRIPTION = """
            보관함에서 항목을 눌렀을 때의 **재생 화면** 데이터다. 목록(§11.5)이 주지 않는
            재생 주소·문구·에셋이 여기 있다.

            - **인증 필요.** 본인 것만 열린다.

            ### 두 URL 의 서명이 다르다 *(ADR-0012)*

            | 필드 | 서명 | 이유 |
            |---|---|---|
            | `videoUrl` | **프리픽스 서명**(`Policy`) | HLS 는 재생목록 안에서 조각을 더 요청한다. 클라이언트가 그 요청에도 **같은 파라미터**를 붙여야 한다 |
            | `thumbnailUrl` | **단일 파일 서명**(`Expires`) | 그 자체로 끝나는 주소다. 만료 6시간 |

            조각이 403 이면 **`POST /api/v1/media/{jobUuid}/signature`** 로 새 파라미터를
            받는다 — 이 API 를 다시 부를 필요가 없다.

            ### description 은 null 일 수 있다

            합성 직후 서버가 결과물을 만들 때는 `NULL` 이고(§11.7), 사용자가 §11.8 로
            채운다. 입력하지 않고 나가면 `null` 인 채로 정상 보관된다.

            ### 안 보이는 결과물은 전부 404

            미존재 · 삭제 · **타인 소유**가 같은 `MOTIF_RESULT_NOT_FOUND` 다. 403 으로
            가르면 "그 결과물은 존재하고 당신 것이 아니다" 가 응답에서 읽히는데,
            `resultSeq` 는 찍어 볼 수 있는 값이다(§1.8).

            **삭제된 아티스트·판매 중단 에셋의 결과물은 열린다** — §11.5 목록에 나오는
            것이 여기서 404 면 목록과 상세가 어긋난다.
            """;

    String DETAIL_SUCCESS_EXAMPLE = """
            {
              "result": "SUCCESS",
              "code": "SUCCESS",
              "message": "요청이 성공했습니다.",
              "data": {
                "resultSeq": 10,
                "videoUrl": "https://cdn.motif.io/private/motif/9c1d2e3f-4a5b-6c7d-8e9f-0a1b2c3d4e5f/master.m3u8?Policy=eyJTdGF0ZW1lbnQiOlt7...&Signature=...&Key-Pair-Id=K27QE85H6DLYDD",
                "thumbnailUrl": "https://cdn.motif.io/private/motif/9c1d2e3f-4a5b-6c7d-8e9f-0a1b2c3d4e5f/thumb.jpg?Expires=1790000000&Signature=...&Key-Pair-Id=K27QE85H6DLYDD",
                "description": null,
                "durationMs": 9800,
                "asset": { "assetSeq": 21, "title": "9월 신규 에셋" },
                "artist": {
                  "artistSeq": 3,
                  "name": "카리나",
                  "groupName": "aespa",
                  "profileImageUrl": "https://cdn.motif.io/profiles/3f9c1a2b.jpg"
                },
                "isRep": false,
                "regDttm": "2026-09-01T10:00:00.000Z"
              },
              "timestamp": "2026-09-10T00:00:00.000Z"
            }""";

    @Operation(summary = "결과물 상세 (§11.6)", description = DETAIL_DESCRIPTION)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "성공",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = DETAIL_SUCCESS_EXAMPLE))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "미존재 · 삭제 · 타인 소유",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = FailResponseSchema.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "result": "FAIL",
                                      "code": "MOTIF_RESULT_NOT_FOUND",
                                      "message": "존재하지 않는 결과물입니다.",
                                      "data": null,
                                      "timestamp": "2026-09-10T00:00:00.000Z"
                                    }""")))
    })
    ApiResponse<MotifResultDetailResponse> detail(
            @Parameter(hidden = true) AuthenticatedUser user,
            @Parameter(description = "결과물 식별자", example = "10") Long resultSeq);

    String NOT_FOUND_EXAMPLE = """
            {
              "result": "FAIL",
              "code": "MOTIF_RESULT_NOT_FOUND",
              "message": "존재하지 않는 결과물입니다.",
              "data": null,
              "timestamp": "2026-09-10T00:00:00.000Z"
            }""";

    @Operation(summary = "결과물 수정 — 문구·대표 설정 (§11.8)", description = """
            **부분 수정**이다. 보낸 필드만 반영한다. 구 §4.5(대표 모티프 설정)가 여기로
            통합됐다(R4) — 문구 수정과 대표 설정이 같은 대상에 대한 수정이기 때문이다.

            | 필드 | 설명 |
            |---|---|
            | `description` | 결과물 문구. **최초 입력과 이후 수정 모두 이 API** 다. **빈 문자열이 "지움"** 이고 `null` 은 "안 보냈다" 다 |
            | `isRep` | `true` 면 대표 지정, `false` 면 해제 |

            **둘 다 생략하면 아무것도 바꾸지 않고 현재 상태를 돌려준다.** 다만 없는
            결과물이면 그때도 404 다.

            ### 대표 모티프는 사용자당 하나다

            - `isRep: true` — `users.rep_motif_result_seq` 를 이 결과물로 **교체**한다.
              **기존 대표는 자동으로 해제된다** — 칸이 하나뿐이라 따로 끄지 않는다.
            - `isRep: false` — **현재 대표일 때만** 해제한다. 대표가 아닌 결과물에 `false`
              를 보내면 아무 일도 일어나지 않는다(200).

            ### 응답

            `data` 는 **§11.6 상세(수정 반영본)** 와 같은 형태다. 조립 지점이 하나라
            수정 직후 화면과 상세 화면이 갈리지 않는다.
            """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "수정 반영본 (§11.6 과 같은 형태)",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = DETAIL_SUCCESS_EXAMPLE))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "미존재 · 삭제 · 타인 소유",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = FailResponseSchema.class),
                            examples = @ExampleObject(value = NOT_FOUND_EXAMPLE)))
    })
    ApiResponse<MotifResultDetailResponse> update(
            @Parameter(hidden = true) AuthenticatedUser user,
            @Parameter(description = "결과물 식별자", example = "10") Long resultSeq,
            MotifResultUpdateRequest request);

    @Operation(summary = "결과물 삭제 (§11.8)", description = """
            **소프트 삭제**다. `motif_results.is_deleted` 만 올리고 S3 산출물·합성 잡은
            건드리지 않는다 — 되돌릴 수 있어야 하고 잡 이력은 §13 의 소유다.

            삭제된 결과물은 §11.5 목록과 §11.6 상세에서 사라진다.

            ### 대표였다면 함께 비운다

            `users.rep_motif_result_seq` 를 **같은 트랜잭션에서** `NULL` 로 만든다. 이
            참조는 순환 참조를 피하려고 FK 없이 둔 논리 참조라 DB 가 정리해 주지 않는다 —
            빠뜨리면 마이페이지(§4.1)가 삭제된 결과물을 대표로 가리킨다.

            ### 다시 지우면 404 다

            멱등으로 200 을 주지 않는다. 조회가 삭제분을 빼고 있어 "이미 지웠다" 와
            "없다" 를 구분할 방법이 없다.
            """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "삭제 성공 — data 는 null",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "result": "SUCCESS",
                                      "code": "SUCCESS",
                                      "message": "요청이 성공했습니다.",
                                      "data": null,
                                      "timestamp": "2026-09-10T00:00:00.000Z"
                                    }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "미존재 · 이미 삭제 · 타인 소유",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = FailResponseSchema.class),
                            examples = @ExampleObject(value = NOT_FOUND_EXAMPLE)))
    })
    ApiResponse<Void> delete(
            @Parameter(hidden = true) AuthenticatedUser user,
            @Parameter(description = "결과물 식별자", example = "10") Long resultSeq);
}
