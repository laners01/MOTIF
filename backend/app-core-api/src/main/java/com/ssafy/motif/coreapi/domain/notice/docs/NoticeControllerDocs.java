package com.ssafy.motif.coreapi.domain.notice.docs;

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
 * 공지 API 문서 계약 (명세 §10).
 *
 * <p>Swagger 애너테이션만 여기 둔다 — 매핑은 구현 클래스가 갖는다
 * (팀 표준, {@code backend/README.md}).
 *
 * <p>예시 JSON 은 상세 명세 {@code docs/api/detail/10-notice.md} 와 <b>같은 값</b>이다.
 */
@Tag(name = "공지 (NOTICE)", description = "명세 §10 — 조회는 §5.5·§6.3 이 대신한다")
public interface NoticeControllerDocs {

    String LIKE_DESCRIPTION = """
            공지 좋아요.

            - **인증 필요.** 요청 본문 없음.
            - **성공은 `204 No Content` 이고 본문이 없다**(§1.2 봉투 예외). `isLiked`·
              `likeCount` 를 돌려주지 않으므로 클라이언트가 **낙관적으로 반영하고 실패 시
              롤백**한다.
            - **멱등이다.** 이미 누른 상태에서 POST 를, 누르지 않은 상태에서 DELETE 를
              보내도 204 이고 `likeCount` 는 움직이지 않는다.
            - 해제는 **하드 삭제**다.

            ### 초기 상태는 조회 API 가 준다

            공지 상세 API 는 **없다**(구 §10.1, v0.8 삭제). 화면 진입 시의 `isLiked`·
            `likeCount` 는 **§5.5 아티스트 공지 목록** 또는 **§6.3 활동 피드의 `NOTICE`
            항목**에서 받는다 — 두 항목의 필드 구성은 같다. 토글 후 정확한 값이 필요하면
            그 둘 중 하나를 다시 받는다.

            ### 404 가 덮는 범위

            미존재 공지와 **삭제된 공지**가 모두 `NOTICE_NOT_FOUND` 다(§1.8 — 삭제분은
            없는 것으로 답한다). 구독 여부·작성 아티스트는 보지 않는다.
            """;

    String NOT_FOUND_EXAMPLE = """
            {
              "result": "FAIL",
              "code": "NOTICE_NOT_FOUND",
              "message": "존재하지 않는 공지입니다.",
              "data": null,
              "timestamp": "2026-09-10T00:00:00.000Z"
            }""";

    @Operation(summary = "공지 좋아요 (§10.2)", description = LIKE_DESCRIPTION)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "204", description = "반영됨 (본문 없음)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "미존재 · 삭제된 공지",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = FailResponseSchema.class),
                            examples = @ExampleObject(value = NOT_FOUND_EXAMPLE)))
    })
    void like(@Parameter(hidden = true) AuthenticatedUser user,
              @Parameter(description = "공지 식별자", example = "301") Integer noticeSeq);

    @Operation(summary = "공지 좋아요 해제 (§10.2)", description = LIKE_DESCRIPTION)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "204", description = "반영됨 (본문 없음)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "미존재 · 삭제된 공지",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = FailResponseSchema.class),
                            examples = @ExampleObject(value = NOT_FOUND_EXAMPLE)))
    })
    void unlike(@Parameter(hidden = true) AuthenticatedUser user,
                @Parameter(description = "공지 식별자", example = "301") Integer noticeSeq);
}
