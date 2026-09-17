package com.ssafy.motif.coreapi.domain.comment.docs;

import com.ssafy.motif.common.response.ApiResponse;
import com.ssafy.motif.common.response.PageResponse;
import com.ssafy.motif.coreapi.domain.comment.dto.CommentItem;
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
 * 댓글 API 문서 계약 (명세 §9.1).
 *
 * <p>Swagger 애너테이션만 여기 둔다 — 매핑은 구현 클래스가 갖는다
 * (팀 표준, {@code backend/README.md}).
 *
 * <p>예시 JSON 은 상세 명세 {@code docs/api/detail/09-comment.md} 와 <b>같은 값</b>이다.
 */
@Tag(name = "댓글 (COMMENT)", description = "명세 §9 — 챌린지·참여 영상·공지 공통 댓글")
public interface CommentControllerDocs {

    String LIST_DESCRIPTION = """
            **세 대상의 댓글이 하나의 계약이다** (명세 §9). 챌린지·참여 영상·공지의 응답
            항목이 완전히 같은 형태라 클라이언트는 댓글 UI 를 한 벌만 들면 된다.

            - **인증 필요.** `isLiked`·`isMine` 과 **정렬**이 호출자 기준이다.
            - 답글은 **접힌 상태**다 — `replyCount` 만 주고 펼치기는 §9.2 다.
            - `regDttm` 은 **원시각**이다. "n시간 전" 표기는 클라이언트가 만든다.

            ### 정렬 — 세 그룹을 순서대로

            1. **대상 아티스트의 댓글** — 최신순
            2. **내 댓글** — 최신순
            3. **일반 댓글** — 최신순

            전체를 이 순서로 정렬한 **뒤에** `size` 만큼 자른다. `size=20` 인데 아티스트
            댓글이 15개, 내 댓글이 10개면 첫 페이지는 **아티스트 15개 + 내 댓글 최신 5개**다.

            "대상 아티스트"는 `role=ARTIST` 전체가 아니라 **그 콘텐츠에 연결된 아티스트**다
            (챌린지·공지는 `artist_seq`, 참여 영상은 원본 챌린지에서 복사된 `artist_seq`).
            **다른 아티스트 계정의 댓글은 일반 댓글**이고, `author.isArtist` 도 같은 판정이다.

            ### 보이지 않는 댓글

            삭제된 댓글과 **삭제된 사용자가 쓴 댓글**은 목록에 없다(§1.8). 루트 댓글
            작성자가 삭제 사용자면 **답글 스레드 전체**가 비노출이다. `replyCount` 도
            노출되는 답글만 센다 — 펼쳤을 때 나오는 수와 같다.
            """;

    String SUCCESS_EXAMPLE = """
            {
              "result": "SUCCESS",
              "code": "SUCCESS",
              "message": "요청이 성공했습니다.",
              "data": {
                "list": [
                  {
                    "commentSeq": 501,
                    "content": "잘 봤어요!",
                    "author": {
                      "userSeq": 5,
                      "nickname": "팬123",
                      "profileImageUrl": "https://cdn.motif.io/profiles/8b2d4e1a.jpg",
                      "isArtist": false
                    },
                    "likeCount": 3,
                    "isLiked": false,
                    "replyCount": 2,
                    "isMine": false,
                    "regDttm": "2026-09-01T12:00:00.000Z"
                  }
                ],
                "page": 0,
                "size": 20,
                "totalElements": 210,
                "totalPages": 11,
                "hasNext": true,
                "hasPrevious": false
              },
              "timestamp": "2026-09-10T00:00:00.000Z"
            }""";

    @Operation(summary = "챌린지 루트 댓글 목록 (§9.1)", description = LIST_DESCRIPTION)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "성공",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = SUCCESS_EXAMPLE))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "미존재 · 삭제된 챌린지",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = FailResponseSchema.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "result": "FAIL",
                                      "code": "CHALLENGE_NOT_FOUND",
                                      "message": "존재하지 않는 챌린지입니다.",
                                      "data": null,
                                      "timestamp": "2026-09-10T00:00:00.000Z"
                                    }""")))
    })
    ApiResponse<PageResponse<CommentItem>> challengeComments(
            @Parameter(hidden = true) AuthenticatedUser user,
            @Parameter(description = "챌린지 식별자", example = "11") Integer challengeSeq,
            @Parameter(description = "0-base 페이지", example = "0") int page,
            @Parameter(description = "페이지 크기 — 최대 100", example = "20") int size);

    @Operation(
            summary = "참여 영상 루트 댓글 목록 (§9.1)",
            description = LIST_DESCRIPTION + """

                    ### 이 대상만 댓글이 잠긴다

                    `comment_enabled=FALSE` 인 영상은 **목록 조회까지 403**(`COMMENT_DISABLED`)
                    이다. 댓글 사용 중지는 **데이터 동결**이라 기존 댓글을 지우지 않고 접근만
                    막는다 — 빈 목록(200)이 아니므로 클라이언트는 "아직 댓글이 없습니다" 와
                    다르게 그린다.

                    **안 보이는 영상은 전부 404 다** — 미존재 · 삭제 · 삭제 사용자의 콘텐츠 ·
                    삭제된 챌린지 소속 · **타인의 `PRIVATE`** 이 같은 `VIDEO_NOT_FOUND` 다
                    (§1.8 존재 은닉). 잠금 판정보다 **먼저** 걸린다 — 403 을 주면 "그 영상은
                    존재한다" 가 응답에서 읽히기 때문이다.
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "성공",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = SUCCESS_EXAMPLE))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403", description = "댓글 사용 중지",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = FailResponseSchema.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "result": "FAIL",
                                      "code": "COMMENT_DISABLED",
                                      "message": "댓글을 사용할 수 없는 게시물입니다.",
                                      "data": null,
                                      "timestamp": "2026-09-10T00:00:00.000Z"
                                    }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "미존재 · 삭제 영상 · 삭제 사용자의 콘텐츠 · 타인의 PRIVATE · 삭제된 챌린지 소속",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = FailResponseSchema.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "result": "FAIL",
                                      "code": "VIDEO_NOT_FOUND",
                                      "message": "존재하지 않는 영상입니다.",
                                      "data": null,
                                      "timestamp": "2026-09-10T00:00:00.000Z"
                                    }""")))
    })
    ApiResponse<PageResponse<CommentItem>> videoComments(
            @Parameter(hidden = true) AuthenticatedUser user,
            @Parameter(description = "참여 영상 식별자", example = "88") Long videoSeq,
            @Parameter(description = "0-base 페이지", example = "0") int page,
            @Parameter(description = "페이지 크기 — 최대 100", example = "20") int size);

    @Operation(summary = "공지 루트 댓글 목록 (§9.1)", description = LIST_DESCRIPTION)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "성공",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = SUCCESS_EXAMPLE))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "미존재 · 삭제된 공지",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = FailResponseSchema.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "result": "FAIL",
                                      "code": "NOTICE_NOT_FOUND",
                                      "message": "존재하지 않는 공지입니다.",
                                      "data": null,
                                      "timestamp": "2026-09-10T00:00:00.000Z"
                                    }""")))
    })
    ApiResponse<PageResponse<CommentItem>> noticeComments(
            @Parameter(hidden = true) AuthenticatedUser user,
            @Parameter(description = "공지 식별자", example = "7") Integer noticeSeq,
            @Parameter(description = "0-base 페이지", example = "0") int page,
            @Parameter(description = "페이지 크기 — 최대 100", example = "20") int size);
}
