package com.ssafy.motif.coreapi.domain.video.docs;

import com.ssafy.motif.common.response.ApiResponse;
import com.ssafy.motif.common.response.CursorResponse;
import com.ssafy.motif.coreapi.domain.video.dto.VideoItem;
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
 * 챌린지별 참여 영상 API 문서 계약 (명세 §8.1).
 *
 * <p>Swagger 애너테이션만 여기 둔다 — 매핑은 구현 클래스가 갖는다
 * (팀 표준, {@code backend/README.md}).
 */
@Tag(name = "참여 영상 (VIDEO)", description = "명세 §8 — 챌린지 참여 영상")
public interface ChallengeVideoControllerDocs {

    @Operation(
            summary = "챌린지별 참여 영상 피드 (§8.1)",
            description = """
                    챌린지 상세에서 들어가는 **세로 뷰어의 목록**이다 (명세 §8.1).
                    **커서 페이지네이션**(§1.9).

                    - **인증 필요.** `isLiked`·`isMine` 이 호출자 기준이다.
                    - `cursor` 를 생략하면 1위부터다. 응답의 `nextCursor` 를 그대로 다시 넣어 다음 장을
                      받는다. `hasNext=false`(= `nextCursor=null`)면 끝이다.
                    - **커서는 서버가 만든 opaque 토큰이다.** 해석·조작하지 마라. 망가진 토큰은
                      400 `INVALID_PARAMETER` 다.
                    - `size` 기본 20, 최대 100.

                    ### 순서는 서버가 정한다 (추천 정렬)

                    `sort` 파라미터가 없다. **아티스트가 좋아요·댓글로 반응한 영상이 맨 위**로 오고,
                    그 안에서 조회수 순이다. 좋아요·댓글 **수**는 순서에 넣지 않는다(§6.1 랭킹과 같은
                    기본안) — 응답 필드로는 그대로 내려간다.

                    점수는 조회 시점에 매기므로, 스크롤 중 조회수가 오른 영상이 **다시 나오거나
                    건너뛸 수** 있다. 순위 목록의 keyset 페이지에서는 피할 수 없고, 커서가 opaque
                    토큰이라 계약이 깨지지는 않는다.

                    ### 목록에서 빠지는 것

                    - 삭제된 영상 · 삭제된 사용자의 영상 (§1.8)
                    - **타인의** `PRIVATE` 영상 — 내 `PRIVATE` 은 보인다(`visibility` 로 자물쇠를 그린다)
                    - 요청자가 **관심 없음**(§8.6) 한 영상

                    ### 항목은 §8.2 와 같은 형태다

                    뷰어가 어느 경로로 들어왔든 같은 오버레이를 그린다. `videoUrl` 은
                    **마스터 재생목록(`master.m3u8`)** 이고 단일 MP4 가 아니다(§1.7) — 참여 영상은
                    `visibility` 와 무관하게 보호 등급이라 **프리픽스 서명**이 붙는다(ADR-0012·0013).

                    영상이 없으면 **빈 목록 200** 이다. 미존재·삭제 챌린지는 404 로 갈린다.
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
                                    "videoSeq": 88,
                                    "challengeSeq": 11,
                                    "description": "재밌었다!",
                                    "videoUrl": "https://cdn.motif.io/private/challenge/3fa85f64-5717-4562-b3fc-2c963f66afa6/master.m3u8",
                                    "thumbnailUrl": "https://cdn.motif.io/private/challenge/3fa85f64-5717-4562-b3fc-2c963f66afa6/thumb.jpg",
                                    "durationMs": 15000,
                                    "author": {
                                      "userSeq": 5,
                                      "nickname": "팬123",
                                      "profileImageUrl": "https://cdn.motif.io/profiles/8b2d4e1a.jpg"
                                    },
                                    "viewCount": 3400,
                                    "likeCount": 120,
                                    "commentCount": 15,
                                    "hasArtistLike": true,
                                    "hasArtistComment": false,
                                    "isLiked": false,
                                    "commentEnabled": true,
                                    "visibility": "PUBLIC",
                                    "isMine": false,
                                    "regDttm": "2026-09-01T10:00:00.000Z"
                                  }
                                ],
                                "prevCursor": null,
                                "nextCursor": "MTAwMDAwMDAwMzQwMDo4OA",
                                "hasPrevious": false,
                                "hasNext": true
                              },
                              "timestamp": "2026-09-16T00:00:00.000Z"
                            }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "망가진 커서",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = FailResponseSchema.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "result": "FAIL",
                                      "code": "INVALID_PARAMETER",
                                      "message": "잘못된 요청 값입니다.",
                                      "data": null,
                                      "timestamp": "2026-09-16T00:00:00.000Z"
                                    }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "미존재·삭제 챌린지",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = FailResponseSchema.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "result": "FAIL",
                                      "code": "CHALLENGE_NOT_FOUND",
                                      "message": "존재하지 않는 챌린지입니다.",
                                      "data": null,
                                      "timestamp": "2026-09-16T00:00:00.000Z"
                                    }""")))
    })
    ApiResponse<CursorResponse<VideoItem>> feed(
            @Parameter(hidden = true) AuthenticatedUser user,
            @Parameter(description = "챌린지 seq", example = "11") Integer challengeSeq,
            @Parameter(description = "이전 응답의 nextCursor. 첫 페이지는 생략") String cursor,
            @Parameter(description = "페이지 크기 (기본 20, 최대 100)", example = "20") int size);
}
