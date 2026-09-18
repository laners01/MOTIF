package com.ssafy.motif.coreapi.domain.challenge.docs;

import com.ssafy.motif.common.response.ApiResponse;
import com.ssafy.motif.common.response.CursorResponse;
import com.ssafy.motif.coreapi.domain.challenge.dto.ChallengeDetailResponse;
import com.ssafy.motif.coreapi.domain.challenge.dto.ChallengeFeedItem;
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
 * 팬 앱 챌린지 조회 API 문서 계약 (명세 §7.1·§7.2).
 *
 * <p>Swagger 애너테이션만 여기 둔다 — 매핑은 구현 클래스가 갖는다
 * (팀 표준, {@code backend/README.md}).
 */
@Tag(name = "챌린지 원본 (CHALLENGE)", description = "명세 §7 — 아티스트 원본 챌린지")
public interface ChallengeControllerDocs {

    @Operation(
            summary = "숏폼 피드 (§7.1)",
            description = """
                    숏폼 화면의 스와이프 목록이다 (명세 §7.1). **커서 페이지네이션**(§1.9).

                    - **인증 필요.** `isLiked`·`isSubscribed` 가 호출자 기준이다.
                    - `cursor` 를 생략하면 첫 페이지다. 응답의 `nextCursor` 를 그대로 다시 넣어 다음 장을 받는다.
                      `hasNext=false`(= `nextCursor=null`)면 끝이다.
                    - **커서는 서버가 만든 opaque 토큰이다.** 해석·조작하지 마라 — 정렬 키가 바뀌어도
                      계약이 깨지지 않는 이유다. 망가진 토큰은 400 `INVALID_PARAMETER` 다.
                    - `size` 기본 20, 최대 100.
                    - `videoUrl` 은 **마스터 재생목록(`master.m3u8`)** 이다 (§1.7, ADR-0010 D2).
                      화질 3종(1080p/720p/480p) 자동 전환(ABR). 후처리 전이면 원본 MP4 일 수 있다.
                    - `thumbnailUrl` 은 첫 프레임 로딩 전 플레이스홀더다 (회의 확정 R2).
                    - **`title` 이 없다**(R3 컬럼 삭제). 화면 식별은 곡명(`song.title`)·아티스트·썸네일이다.
                    - `artist.groupName` 은 없다 (회의 확정).
                    - `participantCount` 는 **PUBLIC·PRIVATE 를 모두 포함**한다.
                    - 삭제된 챌린지는 나오지 않는다 (§1.8 공통 비노출).

                    ### 현재 정렬 — 추천이 아직 없다

                    명세는 "구독 아티스트 + 인기·관심 기반 추천 종합(`rank:challenge` 활용)" 이지만
                    랭킹이 구현되지 않았다. **지금은 최신순**이다. 커서가 opaque 토큰이라 랭킹이
                    붙어도 클라이언트는 고칠 것이 없다.
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
                                    "challengeSeq": 11,
                                    "videoUrl": "https://cdn.../challenges/{uuid}/master.m3u8",
                                    "thumbnailUrl": "https://cdn.../challenges/{uuid}/thumb.jpg",
                                    "durationMs": 15000,
                                    "artist": { "artistSeq": 3, "name": "카리나", "profileImageUrl": "https://cdn.../profiles/3.jpg" },
                                    "song": { "title": "Supernova", "singerName": "aespa" },
                                    "viewCount": 120000,
                                    "participantCount": 340,
                                    "likeCount": 5600,
                                    "commentCount": 210,
                                    "isLiked": false,
                                    "isSubscribed": true
                                  }
                                ],
                                "prevCursor": null,
                                "nextCursor": "MTE",
                                "hasPrevious": false,
                                "hasNext": true
                              },
                              "timestamp": "2026-09-14T00:00:00.000Z"
                            }
                            """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "cursor 토큰이 잘못됨 (`INVALID_PARAMETER`)",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = FailResponseSchema.class)))
    })
    ApiResponse<CursorResponse<ChallengeFeedItem>> feed(
            @Parameter(hidden = true) AuthenticatedUser user,
            @Parameter(description = "이전 응답의 nextCursor. 생략하면 첫 페이지") String cursor,
            @Parameter(description = "페이지 크기 (기본 20, 최대 100)") int size);

    @Operation(
            summary = "챌린지 상세 (§7.2)",
            description = """
                    챌린지 상세 Bottom Sheet 의 데이터다 — §7.1 피드 항목 + `description` + 슬롯 정보 (명세 §7.2).

                    - **인증 필요.** `isLiked`·`isSubscribed` 가 호출자 기준이다.
                    - `videoUrl` 은 **단일 MP4 가 아니라 마스터 재생목록(`master.m3u8`)** 이다 (§1.7, ADR-0010 D2).
                      플레이어가 그 재생목록을 읽고 화질별 재생목록·조각을 이어서 요청한다.
                      챌린지 원본은 **공개 등급**이라 서명 없는 CloudFront URL 이며, 피드에서 참여 영상과
                      같이 스와이프되므로 참여 영상과 마찬가지로 HLS 다.
                    - `thumbnailUrl` 은 첫 프레임 로딩 전 플레이스홀더다 (회의 확정 R2).
                    - **`title` 이 없다** — R3 에서 `challenges.title` 컬럼이 삭제됐다. 화면 식별은
                      곡명(`song.title`)과 아티스트로 한다.
                    - `participantCount` 는 게시 참여 수이며 **PUBLIC·PRIVATE 를 모두 포함**한다.
                      PRIVATE 영상의 조회 비노출(§1.8)과 카운트 집계는 분리된 규칙이다.
                    - `participants` 는 교체 가능한 인물 슬롯이다. `participantMask` 는 **단일 비트**
                      (1·2·4·8)이며 배열은 마스크 오름차순 = 화면 좌→우 순서다.
                      `anchor` 는 촬영 전 선택 화면에서 프로필 원이 앉는 자리로 **썸네일 대비 0~1 비율**
                      (`x`·`r` 가로, `y` 세로)이다 — 어드민이 아직 찍지 않은 옛 슬롯은 `null`.
                    - `editedChallenges` 는 조합(`participantMask`, 비트 OR 합)별 선택 화면 이미지다.
                      고른 슬롯 마스크를 OR 한 항목의 `previewImageUrl` 을 썸네일 자리에 띄우고,
                      무선택(0)·`null`·항목 없음은 `thumbnailUrl` 로 폴백한다.
                      `profileImageUrl` 은 설명 시트의 참여자 아바타용이며 미등록이면 `null` 이다.
                    - **편집본이 0개인 챌린지가 있다.** 슬롯은 등록됐지만 AI 편집본이 아직 없는
                      상태로, 재생·표시는 정상이고 촬영만 §13.1 의 `EDITED_CHALLENGE_NOT_FOUND` 로
                      막힌다. 어드민 등록이 **0개 또는 정확히 2ⁿ−1개**만 허용하므로 부분 집합은
                      존재하지 않는다 — 조합 하나만 비는 경우는 없다.
                    - **`availableMasks` 는 없다** (회의 확정 R4). 조합 유효성은 §13.1 의
                      `EDITED_CHALLENGE_NOT_FOUND` 로만 사후 판별된다.
                    - 삭제된 챌린지는 **없는 것으로 답한다**(404) — §1.8 공통 비노출.

                    댓글은 §9 공통 댓글(`type=challenge`)을 쓴다.
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "챌린지 상세",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "result": "SUCCESS",
                                      "code": "SUCCESS",
                                      "message": "요청이 성공했습니다.",
                                      "data": {
                                        "challengeSeq": 11,
                                        "videoUrl": "https://cdn.../challenges/{uuid}/master.m3u8",
                                        "thumbnailUrl": "https://cdn.../challenges/{uuid}/thumb.jpg",
                                        "durationMs": 15000,
                                        "artist": { "artistSeq": 3, "name": "카리나", "profileImageUrl": "https://cdn.../profiles/3.jpg" },
                                        "song": { "title": "Supernova", "singerName": "aespa" },
                                        "viewCount": 120000,
                                        "participantCount": 340,
                                        "likeCount": 5600,
                                        "commentCount": 210,
                                        "isLiked": false,
                                        "isSubscribed": true,
                                        "description": "같이 춰요!",
                                        "participants": [
                                          {
                                            "artistSeq": 7,
                                            "name": "윈터",
                                            "participantMask": 1,
                                            "profileImageUrl": "https://cdn.../profiles/7.jpg",
                                            "anchor": { "x": 0.22, "y": 0.36, "r": 0.0995 }
                                          }
                                        ]
                                      },
                                      "timestamp": "2026-09-14T00:00:00.000Z"
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
                                      "timestamp": "2026-09-14T00:00:00.000Z"
                                    }""")))
    })
    ApiResponse<ChallengeDetailResponse> detail(
            @Parameter(hidden = true) AuthenticatedUser user,
            @Parameter(description = "챌린지 seq", example = "11") Integer challengeSeq);

    String LIKE_DESCRIPTION = """
            챌린지 좋아요 (§7.3).

            - **인증 필요.** 요청 본문 없음.
            - **성공은 `204 No Content` 이고 본문이 없다**(§1.2 봉투 예외). `isLiked`·
              `likeCount` 를 돌려주지 않으므로 클라이언트가 **낙관적으로 반영하고 실패 시
              롤백**한다 — §8.3 영상 · §9.5 댓글 · §10.2 공지와 같은 계약이다.
            - **멱등이다.** 이미 누른 상태에서 POST 를, 누르지 않은 상태에서 DELETE 를
              보내도 204 이고 `likeCount` 는 움직이지 않는다.
            - 해제는 **하드 삭제**다.

            ### 초기 상태는 조회 API 가 준다

            화면 진입 시의 `isLiked`·`likeCount` 는 §7.1 피드 또는 §7.2 상세가 싣는다.

            ### 404 가 덮는 범위

            미존재 챌린지와 **삭제된 챌린지**가 모두 `CHALLENGE_NOT_FOUND` 다(§1.8 —
            삭제분은 없는 것으로 답한다).
            """;

    @Operation(summary = "챌린지 좋아요 (§7.3)", description = LIKE_DESCRIPTION)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "204", description = "반영됨 (본문 없음)"),
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
                                      "timestamp": "2026-09-18T00:00:00.000Z"
                                    }""")))
    })
    void like(@Parameter(hidden = true) AuthenticatedUser user,
              @Parameter(description = "챌린지 식별자", example = "12") Integer challengeSeq);

    @Operation(summary = "챌린지 좋아요 해제 (§7.3)", description = LIKE_DESCRIPTION)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "204", description = "반영됨 (본문 없음)"),
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
                                      "timestamp": "2026-09-18T00:00:00.000Z"
                                    }""")))
    })
    void unlike(@Parameter(hidden = true) AuthenticatedUser user,
                @Parameter(description = "챌린지 식별자", example = "12") Integer challengeSeq);

}
