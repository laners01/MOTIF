package com.ssafy.motif.coreapi.domain.comment.docs;

import com.ssafy.motif.common.response.ApiResponse;
import com.ssafy.motif.common.response.PageResponse;
import com.ssafy.motif.coreapi.domain.comment.dto.CommentCreateRequest;
import com.ssafy.motif.coreapi.domain.comment.dto.CommentCreateResponse;
import com.ssafy.motif.coreapi.domain.comment.dto.CommentDeleteResponse;
import com.ssafy.motif.coreapi.domain.comment.dto.CommentItem;
import com.ssafy.motif.coreapi.domain.comment.dto.CommentReportRequest;
import com.ssafy.motif.coreapi.domain.comment.dto.ReplyItem;
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
 * 댓글 API 문서 계약 (명세 §9 전량).
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

    @Operation(
            summary = "답글 펼치기 (§9.2)",
            description = """
                    §9.1 이 접어 둔 답글(`replyCount`)을 펼친다.

                    - **인증 필요.** `isLiked`·`isMine` 이 호출자 기준이다.
                    - **등록순 고정**이다 — 최신순이 아니다. 답글은 대화라서 쓴 순서대로 읽힌다.
                    - 항목에 **`replyCount` 가 없다** — 답글에는 답글이 없다(깊이 2단 고정).
                    - `totalElements` 는 §9.1 의 `replyCount` 와 **같은 값**이다.

                    ### 경로의 `{type}`

                    `challenge` | `video` | `notice` — **소문자**다. 댓글 PK 는 테이블마다 따로
                    도는 시퀀스라 `{type}` 없이는 어느 테이블의 `501` 인지 알 수 없다.

                    ### 404 는 여섯 가지를 한 코드로 답한다

                    미존재 · 삭제된 댓글 · **루트가 아님**(답글의 답글을 펼치려는 요청) ·
                    탈퇴 사용자가 쓴 루트(**스레드 전체 비노출**, §1.8) · **대상이 안 보임**
                    (삭제된 챌린지·공지, 타인의 `PRIVATE` 영상) · 알 수 없는 `{type}` 이
                    모두 `COMMENT_NOT_FOUND` 다.

                    대상 쪽 코드(`VIDEO_NOT_FOUND` 등)로 가르지 않는 것은 의도다 — 이 경로는
                    `commentSeq` 만 들고 오므로 대상별 코드를 주면 **그 번호가 어느 테이블의
                    것인지**가 응답에서 읽힌다.
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "성공",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {
                              "result": "SUCCESS",
                              "code": "SUCCESS",
                              "message": "요청이 성공했습니다.",
                              "data": {
                                "list": [
                                  {
                                    "commentSeq": 530,
                                    "content": "😂😂😂",
                                    "author": {
                                      "userSeq": 9,
                                      "nickname": "dancing_mandu",
                                      "profileImageUrl": null,
                                      "isArtist": false
                                    },
                                    "likeCount": 0,
                                    "isLiked": false,
                                    "isMine": false,
                                    "regDttm": "2026-09-01T13:00:00.000Z"
                                  }
                                ],
                                "page": 0,
                                "size": 20,
                                "totalElements": 2,
                                "totalPages": 1,
                                "hasNext": false,
                                "hasPrevious": false
                              },
                              "timestamp": "2026-09-10T00:00:00.000Z"
                            }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403", description = "(참여 영상) 댓글 사용 중지",
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
                    description = "미존재 · 삭제 · 루트 아님 · 탈퇴 작성자의 스레드 · 대상 비노출 · 알 수 없는 type",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = FailResponseSchema.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "result": "FAIL",
                                      "code": "COMMENT_NOT_FOUND",
                                      "message": "존재하지 않는 댓글입니다.",
                                      "data": null,
                                      "timestamp": "2026-09-10T00:00:00.000Z"
                                    }""")))
    })
    ApiResponse<PageResponse<ReplyItem>> replies(
            @Parameter(hidden = true) AuthenticatedUser user,
            @Parameter(description = "challenge | video | notice", example = "challenge") String type,
            @Parameter(description = "루트 댓글 식별자", example = "501") Long commentSeq,
            @Parameter(description = "0-base 페이지", example = "0") int page,
            @Parameter(description = "페이지 크기 — 최대 100", example = "20") int size);

    String WRITE_DESCRIPTION = """
            댓글 또는 답글을 단다. **`parentCommentSeq` 가 있으면 답글**, 없으면 루트 댓글이다.

            - **인증 필요.**
            - **201 Created.** 응답은 `commentSeq` 하나다 — 화면은 로컬 상태로 즉시 그리고,
              정렬(§9.1 은 세 그룹 우선순위다)까지 맞춰야 할 때만 목록을 다시 받는다.
            - 대상의 `commentCount` 가 **같은 트랜잭션에서** 1 올라간다.
            - `content` 는 앞뒤 공백을 떼고 저장한다. **(기본안) 최대 1000자**이고, 공백만
              보내면 400 이다.

            ### 답글 규칙 — 깊이는 2단까지다

            `parentCommentSeq` 는 반드시 **루트 댓글**이어야 하고(답글에 답글 → 400
            `REPLY_DEPTH_EXCEEDED`), **같은 게시물**의 댓글이어야 한다(→ 400
            `PARENT_TARGET_MISMATCH`). 삭제된 부모나 **탈퇴 사용자가 쓴 루트**는 404 다 —
            그 스레드는 §9.1·§9.2 에서 통째로 감춰져 있어(§1.8) 달아도 아무도 볼 수 없다.

            ### 알림

            **이 API 는 알림을 보내지 않는다.** FCM 발송(NOTI-04/05)은 발송 경로가 붙는
            티켓에서 이어진다. 서버는 `notifications` 테이블에 저장하지 않는다(§14).
            """;

    String CREATE_EXAMPLE = """
            {
              "result": "SUCCESS",
              "code": "SUCCESS",
              "message": "요청이 성공했습니다.",
              "data": { "commentSeq": 502 },
              "timestamp": "2026-09-10T00:00:00.000Z"
            }""";

    String PARENT_FAIL_EXAMPLE = """
            {
              "result": "FAIL",
              "code": "REPLY_DEPTH_EXCEEDED",
              "message": "답글에는 답글을 달 수 없습니다.",
              "data": null,
              "timestamp": "2026-09-10T00:00:00.000Z"
            }""";

    @Operation(summary = "챌린지 댓글·답글 작성 (§9.3)", description = WRITE_DESCRIPTION)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201", description = "작성됨",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = CREATE_EXAMPLE))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "본문 검증 실패 · 답글에 답글 · 다른 게시물의 부모",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = FailResponseSchema.class),
                            examples = @ExampleObject(value = PARENT_FAIL_EXAMPLE))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "미존재·삭제된 챌린지 · 부모 댓글 없음",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = FailResponseSchema.class)))
    })
    ApiResponse<CommentCreateResponse> writeOnChallenge(
            @Parameter(hidden = true) AuthenticatedUser user,
            @Parameter(description = "챌린지 식별자", example = "11") Integer challengeSeq,
            CommentCreateRequest request);

    @Operation(
            summary = "참여 영상 댓글·답글 작성 (§9.3)",
            description = WRITE_DESCRIPTION + """

                    ### 잠긴 영상은 403

                    `comment_enabled=FALSE` 면 `COMMENT_DISABLED` 다. 안 보이는 영상
                    (삭제·타인의 `PRIVATE`·삭제된 챌린지 소속)은 그보다 **먼저** 404 로 걸린다.

                    ### 아티스트가 달면 피드가 바뀐다

                    그 영상의 아티스트가 댓글을 달면 `has_artist_comment` 가 켜지고, 이 값은
                    §8.1 피드 정렬에 강하게 반영된다.
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201", description = "작성됨",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = CREATE_EXAMPLE))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "본문 검증 실패 · 답글에 답글 · 다른 게시물의 부모",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = FailResponseSchema.class),
                            examples = @ExampleObject(value = PARENT_FAIL_EXAMPLE))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403", description = "댓글 사용 중지",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = FailResponseSchema.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "안 보이는 영상 · 부모 댓글 없음",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = FailResponseSchema.class)))
    })
    ApiResponse<CommentCreateResponse> writeOnVideo(
            @Parameter(hidden = true) AuthenticatedUser user,
            @Parameter(description = "참여 영상 식별자", example = "88") Long videoSeq,
            CommentCreateRequest request);

    @Operation(summary = "공지 댓글·답글 작성 (§9.3)", description = WRITE_DESCRIPTION)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201", description = "작성됨",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = CREATE_EXAMPLE))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "본문 검증 실패 · 답글에 답글 · 다른 게시물의 부모",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = FailResponseSchema.class),
                            examples = @ExampleObject(value = PARENT_FAIL_EXAMPLE))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "미존재·삭제된 공지 · 부모 댓글 없음",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = FailResponseSchema.class)))
    })
    ApiResponse<CommentCreateResponse> writeOnNotice(
            @Parameter(hidden = true) AuthenticatedUser user,
            @Parameter(description = "공지 식별자", example = "7") Integer noticeSeq,
            CommentCreateRequest request);

    @Operation(
            summary = "댓글·답글 삭제 (§9.4)",
            description = """
                    **본인 댓글만** 지울 수 있다. 소프트 삭제라 행은 남고 목록에서만 사라진다.

                    - **인증 필요.**
                    - **루트를 지우면 직계 답글도 함께 지워진다** — `deletedCount` 가 1보다 클
                      수 있다(루트 1 + 답글 2 = 3). 이미 지워져 있던 답글은 세지 않는다.
                    - 지워진 댓글들의 **좋아요는 하드 삭제**된다. 되살릴 일이 없는 행이다.
                    - 대상의 `commentCount` 가 **실제 삭제된 행 수만큼** 줄어든다(0 아래로
                      내려가지 않는다).
                    - 참여 영상이면 `has_artist_comment` 를 **다시 센다** — 아티스트 댓글을
                      하나 지웠다고 무조건 끄면 다른 아티스트 댓글이 남았는데도 §8.1 피드
                      가산점이 사라진다.

                    ### 잠긴 영상에서도 지울 수 있다

                    `comment_enabled=FALSE` 라도 삭제는 된다(기본안). 잠금은 **새 글을 막는
                    장치**지 이미 쓴 내 글을 못 내리게 하는 장치가 아니다.

                    ### 멱등이 아니다

                    이미 삭제된 댓글은 404 다. 안 보이는 대상(삭제된 챌린지·공지, 타인의
                    `PRIVATE` 영상)에 달린 댓글도 같은 404 다.
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "삭제됨",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {
                              "result": "SUCCESS",
                              "code": "SUCCESS",
                              "message": "요청이 성공했습니다.",
                              "data": { "deletedCount": 3 },
                              "timestamp": "2026-09-10T00:00:00.000Z"
                            }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403", description = "본인 댓글이 아님",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = FailResponseSchema.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "result": "FAIL",
                                      "code": "FORBIDDEN",
                                      "message": "권한이 없습니다.",
                                      "data": null,
                                      "timestamp": "2026-09-10T00:00:00.000Z"
                                    }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "미존재 · 이미 삭제됨 · 대상 비노출 · 알 수 없는 type",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = FailResponseSchema.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "result": "FAIL",
                                      "code": "COMMENT_NOT_FOUND",
                                      "message": "존재하지 않는 댓글입니다.",
                                      "data": null,
                                      "timestamp": "2026-09-10T00:00:00.000Z"
                                    }""")))
    })
    ApiResponse<CommentDeleteResponse> delete(
            @Parameter(hidden = true) AuthenticatedUser user,
            @Parameter(description = "challenge | video | notice", example = "challenge") String type,
            @Parameter(description = "댓글 식별자", example = "501") Long commentSeq);

    String LIKE_DESCRIPTION = """
            댓글·답글 좋아요. **루트와 답글 모두** 누를 수 있다.

            - **인증 필요.** 요청 본문 없음.
            - **성공은 `204 No Content` 이고 본문이 없다**(§1.2 봉투 예외). `isLiked`·
              `likeCount` 를 돌려주지 않으므로 클라이언트가 **낙관적으로 반영하고 실패 시
              롤백**한다. 정확한 값이 필요하면 §9.1·§9.2 를 다시 받는다.
            - **멱등이다.** 이미 누른 상태에서 POST 를, 누르지 않은 상태에서 DELETE 를
              보내도 204 이고 `likeCount` 는 움직이지 않는다.
            - 해제는 **하드 삭제**다.
            - 자기 댓글에도 누를 수 있다(기본안 — 명세에 제한이 없다).

            ### 404 가 덮는 범위

            미존재 · 삭제된 댓글 · **탈퇴 사용자의 댓글** · **감춰진 스레드의 답글**
            (루트 작성자가 탈퇴한 경우 — §1.8) · **대상이 안 보임**(삭제된 챌린지·공지,
            타인의 `PRIVATE` 영상) · 알 수 없는 `{type}` 이 모두 `COMMENT_NOT_FOUND` 다.

            ### 잠긴 영상은 해제도 막는다

            `comment_enabled=FALSE` 면 **생성·해제 모두** 403 이다(명세 §9.5). 자기 댓글을
            지우는 §9.4 와 다른 점이다 — 그쪽은 잠겨도 지울 수 있다.
            """;

    @Operation(summary = "댓글 좋아요 (§9.5)", description = LIKE_DESCRIPTION)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "204", description = "반영됨 (본문 없음)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403", description = "(참여 영상) 댓글 사용 중지",
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
                    responseCode = "404", description = "미존재 · 삭제된 댓글 · 대상 비노출 · 알 수 없는 type",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = FailResponseSchema.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "result": "FAIL",
                                      "code": "COMMENT_NOT_FOUND",
                                      "message": "존재하지 않는 댓글입니다.",
                                      "data": null,
                                      "timestamp": "2026-09-10T00:00:00.000Z"
                                    }""")))
    })
    void like(@Parameter(hidden = true) AuthenticatedUser user,
              @Parameter(description = "challenge | video | notice", example = "challenge") String type,
              @Parameter(description = "댓글 식별자", example = "501") Long commentSeq);

    @Operation(summary = "댓글 좋아요 해제 (§9.5)", description = LIKE_DESCRIPTION)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "204", description = "반영됨 (본문 없음)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403", description = "(참여 영상) 댓글 사용 중지 — 해제도 막는다",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = FailResponseSchema.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "미존재 · 삭제된 댓글 · 대상 비노출 · 알 수 없는 type",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = FailResponseSchema.class)))
    })
    void unlike(@Parameter(hidden = true) AuthenticatedUser user,
                @Parameter(description = "challenge | video | notice", example = "challenge") String type,
                @Parameter(description = "댓글 식별자", example = "501") Long commentSeq);

    @Operation(
            summary = "댓글 신고 (§9.6)",
            description = """
                    댓글·답글을 신고 접수한다. **접수만 한다** — 신고했다고 댓글이 숨겨지거나
                    카운터가 바뀌지 않는다. 판단과 조치는 어드민의 일이다(어드민 §3.13).

                    - **인증 필요.** 성공은 **200** 이고 `data` 는 `null` 이다(§8.7 과 같다).
                      접수 번호를 돌려주지 않는다.
                    - **1인 1대상 1회.** 같은 댓글을 다시 신고하면 409 `ALREADY_REPORTED` 다
                      (DB UNIQUE 로 강제한다).
                    - `reasonCode` 는 **문자열**이다. 사유 코드표가 아직 미결이라
                      (원본 §16 #4 — §8.7·§9.6·§13.3 공통) 서버는 공백 여부와 길이(30자)만 본다.
                    - **본인 댓글도 신고할 수 있다**(기본안) — 막을 근거 코드가 §15 표에 없다.

                    ### 잠긴 영상의 댓글도 신고된다

                    §9.6 실패표에 `COMMENT_DISABLED` 가 없다 — 문제가 되어 잠긴 게시물일수록
                    신고가 필요한데 막으면 어드민에게 알릴 길이 사라진다. (§9.5 좋아요는
                    잠기면 생성·해제 모두 막힌다.)

                    ### 타입이 다르면 같은 번호도 다른 대상이다

                    `reports` 는 4개 대상 칸 중 **정확히 하나만** 채운다. 챌린지 댓글 501 과
                    공지 댓글 501 은 서로 다른 대상이라 각각 신고할 수 있다.
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "접수됨",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {
                              "result": "SUCCESS",
                              "code": "SUCCESS",
                              "message": "요청이 성공했습니다.",
                              "data": null,
                              "timestamp": "2026-09-10T00:00:00.000Z"
                            }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "미존재 · 삭제된 댓글 · 대상 비노출 · 알 수 없는 type",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = FailResponseSchema.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "result": "FAIL",
                                      "code": "COMMENT_NOT_FOUND",
                                      "message": "존재하지 않는 댓글입니다.",
                                      "data": null,
                                      "timestamp": "2026-09-10T00:00:00.000Z"
                                    }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409", description = "이미 신고한 대상",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = FailResponseSchema.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "result": "FAIL",
                                      "code": "ALREADY_REPORTED",
                                      "message": "이미 신고한 대상입니다.",
                                      "data": null,
                                      "timestamp": "2026-09-10T00:00:00.000Z"
                                    }""")))
    })
    ApiResponse<Void> report(
            @Parameter(hidden = true) AuthenticatedUser user,
            @Parameter(description = "challenge | video | notice", example = "challenge") String type,
            @Parameter(description = "댓글 식별자", example = "501") Long commentSeq,
            CommentReportRequest request);
}
