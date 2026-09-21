package com.ssafy.motif.coreapi.domain.challenge.docs;

import com.ssafy.motif.common.response.ApiResponse;
import com.ssafy.motif.common.response.CursorResponse;
import com.ssafy.motif.common.response.ListResponse;
import com.ssafy.motif.coreapi.domain.challenge.dto.ChallengeDetailResponse;
import com.ssafy.motif.coreapi.domain.challenge.dto.ChallengeFeedItem;
import com.ssafy.motif.coreapi.domain.challenge.dto.ChallengeParticipantItem;
import com.ssafy.motif.coreapi.domain.view.dto.ViewRequest;
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
 * 팬 앱 챌린지 API 문서 계약 (명세 §7.1·§7.2·§7.3·§7.4).
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
                    - **`participants[]` 가 없다** (2026-09-21 — §7.5 로 분리). 촬영 선택 화면이
                      쓰는 데이터라 이 시트와 수명이 다르고, 편집본 이미지에 서명 만료가 붙으면서
                      상세를 열어 둔 채 오래 머물면 죽는 값이 됐다. 참여자 아바타가 필요한 화면은
                      §7.5 `GET /challenges/{challengeSeq}/participants` 를 부른다.
                    - **`availableMasks` 도 없다** (회의 확정 R4). 고를 수 있는지는 §7.5 의
                      `initialSceneUrl` 유무가 답하고, 그래도 없는 슬롯을 보내면 §13.1 의
                      `EDITED_CHALLENGE_NOT_FOUND` 가 사후 판별한다.
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
                                        "description": "같이 춰요!"
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

    @Operation(
            summary = "챌린지 슬롯 목록 (§7.5)",
            description = """
                    촬영 전 **"함께할 아티스트" 선택 화면**(시안 10-1~3)의 데이터다 (2026-09-21 신설).

                    - **인증 필요.**
                    - 배열은 `slotOrder` 오름차순 = **화면 좌→우**. 슬롯은 최대 4개라 비페이지다(§5.4 봉투).
                    - **영상이 없다.** 편집본 영상은 슬롯을 고른 뒤 §7.6 으로 따로 받는다 —
                      화면이 갈리고, 서명 URL 은 만료가 있어 필요한 시점에 받아야 한다.
                    - **슬롯은 하나만 고른다.** 여러 명을 한 번에 교체하는 조합은 없다 —
                      편집본이 슬롯 1개당 1개이기 때문이다. 고른 슬롯의 `challengeParticipantSeq` 를
                      §7.6·§13.1 에 그대로 보낸다. **비트 마스크는 앱 계약에서 사라졌다.**
                    - **`initialSceneUrl` 이 `null` 이면 그 슬롯은 고를 수 없다.** 편집본(그 사람을
                      지운 영상)이 아직 없다는 뜻이고, DB 가 영상 ↔ 첫 장면을 짝으로 강제하므로
                      이 값의 유무가 곧 판정이다. 그대로 §13.1 에 보내면
                      `EDITED_CHALLENGE_NOT_FOUND` 이므로 **화면에서 미리 잠근다.**
                    - `previewImageUrl` 은 그 슬롯을 골랐을 때 썸네일 자리에 보이는 이미지다.
                      `null` 이면 §7.2 의 `thumbnailUrl` 로 폴백한다. 아무 슬롯도 안 고른 상태도 같다.
                    - `anchor` 는 프로필 원이 앉는 자리로 **썸네일 대비 0~1 비율**(`x`·`r` 가로,
                      `y` 세로)이다 — 어드민이 아직 찍지 않은 옛 슬롯은 `null` 이라 원을 숨긴다.
                    - **이미지 두 장은 서명 URL 이다.** 키가 `private/edited/…` 라 만료가 있고,
                      만료되면 이 API 를 다시 부른다.
                    - **슬롯이 전부 잠긴 챌린지가 있다.** 슬롯은 등록됐지만 AI 편집본이 아직 없는
                      상태로, 재생·표시는 정상이고 촬영만 막힌다. 일부만 비는 것도 정상이다.
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "슬롯 목록",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "result": "SUCCESS",
                                      "code": "SUCCESS",
                                      "message": "요청이 성공했습니다.",
                                      "data": {
                                        "list": [
                                          {
                                            "challengeParticipantSeq": 31,
                                            "artistSeq": 7,
                                            "name": "윈터",
                                            "slotOrder": 0,
                                            "profileImageUrl": "https://cdn.../profiles/7.jpg",
                                            "anchor": { "x": 0.22, "y": 0.36, "r": 0.0995 },
                                            "initialSceneUrl": "https://cdn.../private/edited/11/e1.jpg?Expires=...&Signature=...&Key-Pair-Id=...",
                                            "previewImageUrl": "https://cdn.../private/edited/11/p1.jpg?Expires=...&Signature=...&Key-Pair-Id=..."
                                          },
                                          {
                                            "challengeParticipantSeq": 32,
                                            "artistSeq": 9,
                                            "name": "닝닝",
                                            "slotOrder": 1,
                                            "profileImageUrl": "https://cdn.../profiles/9.jpg",
                                            "anchor": { "x": 0.72, "y": 0.34, "r": 0.0995 },
                                            "initialSceneUrl": null,
                                            "previewImageUrl": null
                                          }
                                        ]
                                      },
                                      "timestamp": "2026-09-21T00:00:00.000Z"
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
                                      "timestamp": "2026-09-21T00:00:00.000Z"
                                    }""")))
    })
    ApiResponse<ListResponse<ChallengeParticipantItem>> participants(
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

    @Operation(
            summary = "조회 이벤트 (§7.4)",
            description = """
                    챌린지 영상이 재생되면 호출한다 (명세 §7.4). **여러 챌린지의 조회수를 묶어
                    보낸다** — 경로에 `{challengeSeq}` 가 없다(2026-09-21 재정의 — ADR-0016).

                    ```json
                    { "views": { "11": 3, "12": 1 } }
                    ```

                    **§8.5(참여 영상 조회 이벤트)와 같은 계약이다** — 요청 형태·상한·실패 코드가
                    모두 같다. 안드로이드가 두 API 를 같은 코드로 다룰 수 있다.

                    - **인증 필요.**
                    - **202 Accepted.** 집계는 비동기다 — `viewCount` 가 즉시 오르지 않는다.
                      DB 반영은 **20분 주기 배치**가 하고, 그 결과가 §5.1 아티스트 랭킹의 재료다.
                    - 하나라도 어기면 **요청 전체가 400** 이다: `views` 항목 수 1~50,
                      대상당 `count` 1~100.
                    - **404 가 없다.** 존재하지 않는 `challengeSeq` 를 보내도 202 다 — 조회
                      이벤트마다 DB 를 확인하면 집계를 Redis 로 뺀 의미가 없다. Redis 장애도
                      202 다(fail-open).
                    - **1회 인정 기준은 미결**(원본 §16 #3)이라 서버는 보낸 `count` 를 믿는다.
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "202", description = "접수 (집계는 비동기)",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {
                              "result": "SUCCESS",
                              "code": "SUCCESS",
                              "message": "요청이 성공했습니다.",
                              "data": null,
                              "timestamp": "2026-09-21T00:00:00.000Z"
                            }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "views 가 비었음 · 항목 수 초과 · count 범위 위반 · 본문 형식 오류",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = FailResponseSchema.class)))
    })
    ApiResponse<Void> view(ViewRequest request);

}
