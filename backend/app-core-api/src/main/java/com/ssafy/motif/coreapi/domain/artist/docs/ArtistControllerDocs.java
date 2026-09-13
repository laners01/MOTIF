package com.ssafy.motif.coreapi.domain.artist.docs;

import com.ssafy.motif.common.response.ApiResponse;
import com.ssafy.motif.common.response.CursorResponse;
import com.ssafy.motif.common.response.ListResponse;
import com.ssafy.motif.coreapi.domain.artist.dto.ArtistChallengeResponse;
import com.ssafy.motif.coreapi.domain.artist.dto.ArtistDetailResponse;
import com.ssafy.motif.coreapi.domain.artist.dto.ArtistNoticeResponse;
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
 * 팬 앱 아티스트 조회 API 문서 계약 (명세 §5.3 · §5.4 · §5.5).
 *
 * <p>Swagger 애너테이션만 여기 둔다 — 매핑은 구현 클래스가 갖는다
 * (팀 표준, {@code backend/README.md}).
 */
@Tag(name = "아티스트 (ARTIST)", description = "명세 §5 — 아티스트 상세·챌린지 그리드·공지 목록")
public interface ArtistControllerDocs {

    @Operation(
            summary = "아티스트 상세 (§5.3)",
            description = """
                    아티스트 상세 화면 1회 진입 시 호출한다 (명세 §5.3).

                    - **인증 필요.** 응답의 `isSubscribed`·`subscribedDays`·`pass` 가 호출자 기준이다.
                    - `name` 은 `users.nickname` 이다 — `artists` 테이블에 이름 칸이 없다.
                    - `groupName` 은 솔로 아티스트면 `null`.
                    - `challengeCount` 는 **비삭제** 챌린지 수다.
                    - `subscribedDays` 는 구독 경과일("WITH n days")이며 **미구독이면 `null`** 이다.
                    - `pass.active=false` 면 클라이언트는 "모티프 패스 알아보기"(→ §11.4)를 노출한다.
                    - 삭제된 아티스트는 **없는 것으로 답한다**(404) — §1.8 공통 비노출.

                    ### `myMotifVideos` 는 현재 항상 빈 배열이다
                    이 필드는 §1.7 **보호 등급**(모티프 결과물)이라 CloudFront **signed 쿠키**로만
                    접근할 수 있는데, 쿠키 발급이 아직 구현되지 않았다. 공개 URL 로 대신 내보내면
                    유료·비공개 결과물이 무기명 링크로 새므로 빈 배열로 둔다 —
                    명세대로 **빈 배열이면 클라이언트는 상단 자동재생 영역을 노출하지 않는다.**
                    개수·만료 정책은 명세 §16 #23 미결 안건이다.

                    전체 목록·더보기는 §5.8(`GET /users/me/motif-results?artistSeq=`)을 재사용한다.
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "아티스트 상세",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "result": "SUCCESS",
                                      "code": "SUCCESS",
                                      "message": "요청이 성공했습니다.",
                                      "data": {
                                        "artistSeq": 3,
                                        "name": "카리나",
                                        "groupName": "aespa",
                                        "profileImageUrl": "https://cdn.../profiles/3.jpg",
                                        "bio": "aespa의 카리나입니다.",
                                        "subscriberCount": 15230,
                                        "challengeCount": 121,
                                        "isSubscribed": true,
                                        "subscribedDays": 492,
                                        "pass": { "active": true, "expiresDttm": "2026-10-01T00:00:00.000Z" },
                                        "myMotifVideos": []
                                      },
                                      "timestamp": "2026-09-14T00:00:00.000Z"
                                    }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "미존재·삭제 아티스트",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = FailResponseSchema.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "result": "FAIL",
                                      "code": "ARTIST_NOT_FOUND",
                                      "message": "존재하지 않는 아티스트입니다.",
                                      "data": null,
                                      "timestamp": "2026-09-14T00:00:00.000Z"
                                    }""")))
    })
    ApiResponse<ArtistDetailResponse> detail(
            @Parameter(hidden = true) AuthenticatedUser user,
            @Parameter(description = "아티스트 seq", example = "3") Integer artistSeq);

    @Operation(
            summary = "아티스트 챌린지 그리드 (§5.4)",
            description = """
                    해당 아티스트의 챌린지를 **페이지 없이 전량** 최신순으로 반환한다 (회의 확정 R2).

                    - **인증 필요.**
                    - 그리드 경량 항목이다 — `title` 도 `videoUrl` 도 없다.
                      (R3 에서 `challenges.title` 컬럼 자체가 삭제됐다. 화면 식별은 곡명·아티스트로 한다.)
                    - 재생에 필요한 정보는 항목을 탭한 뒤 §7.2 상세에서 받는다.
                    - 삭제된 챌린지는 빠진다 (§1.8).
                    - `page`·`cursor` 파라미터가 **없다** — 전량 반환이다.
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "챌린지 전량 (최신순)",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "result": "SUCCESS",
                                      "code": "SUCCESS",
                                      "message": "요청이 성공했습니다.",
                                      "data": {
                                        "list": [
                                          { "challengeSeq": 11, "thumbnailUrl": "https://cdn.../challenges/uuid/thumb.jpg", "viewCount": 120000 }
                                        ]
                                      },
                                      "timestamp": "2026-09-14T00:00:00.000Z"
                                    }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "미존재·삭제 아티스트",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = FailResponseSchema.class)))
    })
    ApiResponse<ListResponse<ArtistChallengeResponse>> challenges(
            @Parameter(description = "아티스트 seq", example = "3") Integer artistSeq);

    @Operation(
            summary = "아티스트 공지 목록 (§5.5) — 커서 페이지네이션",
            description = """
                    해당 아티스트의 공지를 **최신순 고정**으로 반환한다 (명세 §5.5).

                    - **인증 필요.** `isLiked` 가 호출자 기준이다.
                    - **커서 방식이다**(§1.9) — 어드민 목록(§1.5 offset)과 다르다. `page` 파라미터가 없다.
                      최초 호출은 `cursor` 를 **생략**하고, 이후에는 직전 응답의 `nextCursor` 를 그대로 넣는다.
                    - `nextCursor: null`(= `hasNext: false`)이면 끝이다.
                    - **커서는 opaque 토큰이다** — 해석·조작하지 않는다. 망가진 값은 400 이다.
                    - `prevCursor`/`hasPrevious` 는 자리만 잡아 둔 칸으로 **항상 `null`·`false`** 다.
                      역방향 조회 제공 여부는 §16 미결 안건이고, 현재 계약은 `nextCursor` 순방향뿐이다.
                    - **별도의 공지 상세 API 가 없다**(구 §10.1 삭제) — 목록 항목이 본문(`content`)과
                      이미지 배열을 전부 싣는다. `title` 은 없다.
                    - `images` 는 `displayOrder` 오름차순 **전체 배열**이며, 대표 이미지 전용 필드는 쓰지 않는다.
                    - `artist` 는 `{artistSeq, name, profileImageUrl}` 까지다 — `groupName` 은 제외.
                    - 삭제된 공지는 빠진다 (§1.8).
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "공지 한 페이지 (최신순)",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "result": "SUCCESS",
                                      "code": "SUCCESS",
                                      "message": "요청이 성공했습니다.",
                                      "data": {
                                        "list": [
                                          {
                                            "noticeSeq": 5,
                                            "artist": { "artistSeq": 3, "name": "카리나", "profileImageUrl": "https://cdn.../profiles/3.jpg" },
                                            "content": "전체 본문...",
                                            "images": [
                                              { "imageUrl": "https://cdn.../notices/1.jpg", "displayOrder": 0 },
                                              { "imageUrl": "https://cdn.../notices/2.jpg", "displayOrder": 1 }
                                            ],
                                            "likeCount": 200,
                                            "commentCount": 14,
                                            "isLiked": true,
                                            "regDttm": "2026-09-01T09:00:00.000Z"
                                          }
                                        ],
                                        "prevCursor": null,
                                        "nextCursor": "NQ",
                                        "hasPrevious": false,
                                        "hasNext": true
                                      },
                                      "timestamp": "2026-09-14T00:00:00.000Z"
                                    }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "해석할 수 없는 cursor",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = FailResponseSchema.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "result": "FAIL",
                                      "code": "INVALID_PARAMETER",
                                      "message": "요청 값이 올바르지 않습니다.",
                                      "data": null,
                                      "timestamp": "2026-09-14T00:00:00.000Z"
                                    }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "미존재·삭제 아티스트",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = FailResponseSchema.class)))
    })
    ApiResponse<CursorResponse<ArtistNoticeResponse>> notices(
            @Parameter(hidden = true) AuthenticatedUser user,
            @Parameter(description = "아티스트 seq", example = "3") Integer artistSeq,
            @Parameter(description = "직전 응답의 nextCursor. 최초 호출은 생략한다") String cursor,
            @Parameter(description = "페이지 크기 — 기본 20 · 최대 100", example = "20") int size);
}
