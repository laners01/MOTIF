package com.ssafy.motif.coreapi.domain.artist.docs;

import com.ssafy.motif.common.response.ApiResponse;
import com.ssafy.motif.common.response.ListResponse;
import com.ssafy.motif.coreapi.domain.artist.dto.ArtistChallengeResponse;
import com.ssafy.motif.coreapi.domain.artist.dto.ArtistDetailResponse;
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
 * 팬 앱 아티스트 조회 API 문서 계약 (명세 §5.3 · §5.4).
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
}
