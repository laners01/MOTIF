package com.ssafy.motif.coreapi.domain.artist.docs;

import com.ssafy.motif.common.response.ApiResponse;
import com.ssafy.motif.common.response.CursorResponse;
import com.ssafy.motif.common.response.ListResponse;
import com.ssafy.motif.common.response.PageResponse;
import com.ssafy.motif.coreapi.domain.artist.dto.ArtistCardResponse;
import com.ssafy.motif.coreapi.domain.artist.dto.ArtistChallengeResponse;
import com.ssafy.motif.coreapi.domain.artist.dto.ArtistDetailResponse;
import com.ssafy.motif.coreapi.domain.artist.dto.ArtistMotifPassResponse;
import com.ssafy.motif.coreapi.domain.artist.dto.ArtistNoticeResponse;
import com.ssafy.motif.coreapi.domain.artist.dto.ArtistRankingResponse;
import com.ssafy.motif.coreapi.domain.artist.dto.RankingFilter;
import com.ssafy.motif.coreapi.domain.artist.dto.SubscriptionResponse;
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
 * 팬 앱 아티스트 API 문서 계약
 * (명세 §5.1 · §5.3 · §5.4 · §5.5 · §5.6 · §5.7 · §5.10 · §11.4).
 *
 * <p>Swagger 애너테이션만 여기 둔다 — 매핑은 구현 클래스가 갖는다
 * (팀 표준, {@code backend/README.md}).
 *
 * <p>§11.4 패스는 §11 도메인이지만 경로가 {@code /artists/{artistSeq}} 아래라 여기 있다 —
 * 근거는 {@code ArtistController} javadoc.
 */
@Tag(name = "아티스트 (ARTIST)",
        description = "명세 §5 — 전체 목록·랭킹·상세·챌린지 그리드·공지 목록·구독 · §11.4 모티프 패스")
public interface ArtistControllerDocs {

    @Operation(
            summary = "아티스트 랭킹 (§5.1)",
            description = """
                    Redis 스냅샷 기반의 **서버 고정 정렬**이다. `sort` 파라미터를 받지 않는다.

                    홈 "Who's On TOP4"(§6.2)는 `size=4`, 온보딩 인기 아티스트 목록(§3.1)도
                    **같은 API 를 재사용**한다 — 필드도 캐시 정책도 같아 나눌 실익이 없다.

                    - `rank` 는 **전체 스냅샷 기준**이다. `filter` 로 걸러도 다시 매기지 않는다 —
                      "내 구독 아티스트 중 3위"가 아니라 "전체 3위"를 보여준다.
                    - `rankDelta` = 직전 스냅샷 순위 − 현재 순위. **상승이 양수·하락이 음수**
                      (5위→3위 = `2`, 3위→5위 = `-2`). **신규 진입은 `null`** 이다.
                    - `totalElements` 는 **`filter` 를 적용한 뒤**의 개수다.
                    - `name` 은 `users.nickname`, `groupName` 은 솔로면 `null` 이다.
                    - 스냅샷이 비어 있으면 **빈 목록 200** 이다(에러가 아니다).

                    ### 점수 공식
                    ```
                    score = Σ_c ( view_count_c × 1 + participant_count_c × 100 ) × 0.5 ^ (age_c / 14일)
                    ```
                    해당 아티스트의 **비삭제 챌린지**가 받은 반응을, 그 챌린지의 **등록 시각 기준으로
                    감쇠**시켜 더한다. 챌린지를 올리는 행위 자체에는 점수를 주지 않는다 — 주면 반응
                    없는 챌린지를 여러 개 올리는 것만으로 순위가 올라 인기 랭킹이 아니게 된다.
                    가중치·반감기는 `motif.ranking.*` 설정이다.

                    ### 지금은 전 아티스트가 0점 동점이다 — 버그가 아니다
                    점수의 두 입력을 올리는 경로가 아직 없다 — `view_count` 는 §7.4 조회 이벤트,
                    `participant_count` 는 참여 영상 업로드가 선행 티켓이다. 그 전까지 랭킹은
                    **`artistSeq` ASC 고정 목록**이고 `rankDelta` 는 전원 `null` 이다.
                    두 티켓이 머지되면 **코드 변경 없이** 순위가 갈리기 시작한다.

                    ### 갱신과 캐싱 (§1.6)
                    스냅샷은 **5분**마다(`rank:artist:current`), `rankDelta` 의 기준은 **10분**마다
                    (`rank:artist:prev`) 갱신된다. **서버는 갱신을 push 하지 않는다** — SSE·WebSocket 을
                    쓰지 않고 FCM 은 §14 전용이다. 클라이언트는 **화면 진입·포그라운드 복귀·당겨서
                    새로고침** 시점에만 호출하고, 마지막 응답이 **5분** 안이면 요청하지 않는다
                    (stale-while-revalidate). **타이머 폴링 금지.**
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "아티스트 랭킹",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "result": "SUCCESS",
                                      "code": "SUCCESS",
                                      "message": "요청이 성공했습니다.",
                                      "data": {
                                        "list": [
                                          {
                                            "rank": 1,
                                            "rankDelta": 2,
                                            "artistSeq": 3,
                                            "name": "카리나",
                                            "groupName": "aespa",
                                            "profileImageUrl": "https://cdn.motif.io/profiles/3f9c1a2b.jpg",
                                            "isSubscribed": false
                                          },
                                          {
                                            "rank": 2,
                                            "rankDelta": null,
                                            "artistSeq": 7,
                                            "name": "윈터",
                                            "groupName": "aespa",
                                            "profileImageUrl": null,
                                            "isSubscribed": true
                                          }
                                        ],
                                        "page": 0,
                                        "size": 20,
                                        "totalElements": 58,
                                        "totalPages": 3,
                                        "hasNext": true,
                                        "hasPrevious": false
                                      },
                                      "timestamp": "2026-09-10T00:00:00.000Z"
                                    }
                                    """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "인증 실패 (§1.4 공통)",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = FailResponseSchema.class)))
    })
    ApiResponse<PageResponse<ArtistRankingResponse>> ranking(
            @Parameter(hidden = true) AuthenticatedUser user,
            @Parameter(description = "구독 여부 필터", example = "ALL") RankingFilter filter,
            @Parameter(description = "0-base 페이지 번호", example = "0") int page,
            @Parameter(description = "페이지 크기 — 최대 100. 홈 TOP4 는 4", example = "20") int size);

    @Operation(
            summary = "전체 아티스트 목록 (§5.10) — 구독자 수순 전량",
            description = """
                    아티스트 탭의 전체 목록이다. **비페이지 전량 반환**이고 정렬은
                    `artists.subscriber_count` **내림차순**이다.

                    - **인증 필요.** `isSubscribed` 가 호출자 기준이다.
                    - **파라미터가 없다** — `page`·`size`·`cursor` 도, `sort` 도 받지 않는다.
                      정렬은 서버 고정이다.
                    - **동점은 `artistSeq` 오름차순**이다. tie-break 가 없으면 구독자 0인 신인들의
                      순서가 요청마다 달라져 같은 화면이 새로고침마다 뒤바뀐다.
                    - 항목은 **아티스트 요약 + `isSubscribed`** 다. `name` 은 `users.nickname`,
                      `groupName` 은 솔로면 `null` 이고, `profileImageUrl` 은 공개 등급이라
                      서명하지 않는다(§1.7).
                    - **`subscriberCount` 는 싣지 않는다** — 정렬 기준일 뿐이다. 개별 아티스트의
                      구독자 수는 §5.3 상세가 준다.
                    - 삭제된 아티스트는 빠진다(§1.8). 아티스트가 0명이면 **빈 배열 200** 이다.

                    ### §5.1 랭킹과 다른 API 다
                    | | §5.1 랭킹 | §5.10 목록 |
                    |---|---|---|
                    | 1차 출처 | Redis 스냅샷(배치가 5분마다) | **DB 직조회** |
                    | 정렬 | 챌린지 조회·참여 점수(시간 감쇠) | **구독자 수** |
                    | 페이지 | offset | **없음 — 전량** |

                    §5.1 을 `size` 만 키워 재사용하면 **구독자 수순이 되지 않는다** — 랭킹 점수는
                    구독을 보지 않는다(§5.6).
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "아티스트 전량 (구독자 수 내림차순)",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "result": "SUCCESS",
                                      "code": "SUCCESS",
                                      "message": "요청이 성공했습니다.",
                                      "data": {
                                        "list": [
                                          {
                                            "artistSeq": 3,
                                            "name": "카리나",
                                            "groupName": "aespa",
                                            "profileImageUrl": "https://cdn.motif.io/profiles/3f9c1a2b.jpg",
                                            "isSubscribed": true
                                          },
                                          {
                                            "artistSeq": 7,
                                            "name": "윈터",
                                            "groupName": "aespa",
                                            "profileImageUrl": null,
                                            "isSubscribed": false
                                          }
                                        ]
                                      },
                                      "timestamp": "2026-09-18T00:00:00.000Z"
                                    }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "인증 실패 (§1.4 공통)",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = FailResponseSchema.class)))
    })
    ApiResponse<ListResponse<ArtistCardResponse>> list(
            @Parameter(hidden = true) AuthenticatedUser user);

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
                    이 필드는 §1.7 **보호 등급**(모티프 결과물)이라 CloudFront **signed URL**(재생목록은 core-api 가 다시 써서 내린다)로만
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

    @Operation(
            summary = "구독 (§5.6)",
            description = """
                    아티스트를 구독한다. **무료 팔로우이며 모티프 패스 구매(§11.4)와 완전히 별개**다.

                    **요청 본문이 없다.** 토글이 아니라 "구독 상태로 만들어라"는 단언이라 보낼 값이 없다.

                    ### 멱등하다
                    **이미 구독 중인데 다시 호출해도 200 이고 `subscriberCount` 는 변하지 않는다.**
                    서버는 삽입이 실제로 행을 만들었을 때만 카운터를 올린다(복합 PK 가 판정한다).
                    동시에 두 번 들어와도 카운터는 한 번만 움직인다.

                    ### 연타 처리는 클라이언트 몫이다 (§5.6)
                    서버가 멱등이라 정합성은 항상 유지되므로, 방어는 클라이언트가 한다.
                    1. 버튼을 누르면 **즉시 낙관적으로 UI 를 반전**한다
                    2. **디바운스(약 500ms) 후 최종 상태만 1회 전송**한다
                    3. 응답의 `isSubscribed`·`subscriberCount` 로 UI 를 **보정**한다
                    4. 요청이 진행 중이면 중복 탭을 무시한다

                    **좋아요와 달리 204 가 아니라 200 이다** — §1.2 의 "성공만 204" 예외는
                    좋아요 토글 전용이고, 이쪽은 위 3번의 보정 값을 돌려줘야 한다.

                    ### 랭킹 순위는 움직이지 않는다
                    §5.1 랭킹 점수는 **챌린지 활동**(조회·참여)만 본다. 구독해도 순위는 그대로이며,
                    바뀌는 것은 §5.1 의 `SUBSCRIBED`/`UNSUBSCRIBED` **필터 결과**와
                    §5.3 `isSubscribed`·§4.6 구독 목록·§4.7 구독 카드다.

                    **삭제된 아티스트는 구독할 수 없다** — 404 다(§1.8 없는 것으로 답한다).
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "구독 상태",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "result": "SUCCESS",
                                      "code": "SUCCESS",
                                      "message": "요청이 성공했습니다.",
                                      "data": { "isSubscribed": true, "subscriberCount": 15231 },
                                      "timestamp": "2026-09-10T00:00:00.000Z"
                                    }
                                    """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "미존재·삭제 아티스트",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = FailResponseSchema.class)))
    })
    ApiResponse<SubscriptionResponse> subscribe(
            @Parameter(hidden = true) AuthenticatedUser user,
            @Parameter(description = "아티스트 seq", example = "3") Integer artistSeq);

    @Operation(
            summary = "구독 취소 (§5.7)",
            description = """
                    구독을 해제한다. **하드 삭제**이며 구독 기록이 남지 않는다 —
                    다시 구독하면 `subscribedDays`(§5.3)가 0부터 다시 센다.

                    ### 멱등하다
                    **구독하지 않은 상태에서 호출해도 200 이고 `subscriberCount` 는 변하지 않는다.**
                    서버는 삭제가 실제로 행을 지웠을 때만 카운터를 내린다.

                    ### 404 범위가 §5.6 과 다르다
                    | 대상 | §5.6 구독 | §5.7 취소 |
                    |---|---|---|
                    | 존재하지 않는 `artistSeq` | 404 | 404 |
                    | **삭제된 아티스트** | 404 | **200 — 해제할 수 있다** |

                    삭제된 아티스트 구독은 §4.6 목록에서 이미 숨겨지는데 해제까지 막으면
                    사용자가 그 구독을 **영원히 정리할 수 없다.**

                    연타 규약·204 아닌 이유는 §5.6 과 같다.
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "구독 상태",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "result": "SUCCESS",
                                      "code": "SUCCESS",
                                      "message": "요청이 성공했습니다.",
                                      "data": { "isSubscribed": false, "subscriberCount": 15230 },
                                      "timestamp": "2026-09-10T00:00:00.000Z"
                                    }
                                    """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "존재하지 않는 아티스트 (삭제된 아티스트는 200)",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = FailResponseSchema.class)))
    })
    ApiResponse<SubscriptionResponse> unsubscribe(
            @Parameter(hidden = true) AuthenticatedUser user,
            @Parameter(description = "아티스트 seq", example = "3") Integer artistSeq);

    @Operation(summary = "아티스트별 모티프 패스 (§11.4)", description = """
            패스 구매 화면의 데이터다 — **배너 · 내 보유 상태 · 지금 파는 가격**.
            구 경로 `/motif/shop/artists/{artistSeq}` 를 REST 관점에 맞게 바꾼 것이다.

            - **인증 필요.** `passActive`·`expiresDttm` 이 호출자 기준이다.
            - `prices` 는 **판매 중인** 개월 수별 가격이고 **개월 오름차순**이다. 꺼진
              가격은 나오지 않는다 — 내보내면 눌러도 결제가 시작되지 않는 버튼이 생긴다.
            - 가격이 0건이어도 200 이다(빈 배열). 판매 준비 중인 아티스트가 그렇다.

            ### 보유 중이면 prices 가 빈 배열이다

            현재 구현은 **만료 전 연장 구매를 지원하지 않는다**. 필드가 빠지지는 않는다 —
            두 응답에 같은 타입을 쓸 수 있어야 한다.

            ### 유효 판정

            `status='ACTIVE' AND expires_dttm > now` 이고 **비교는 DB 가 한다**. `status`
            만 보면 만료 배치가 돌기 전 구간에서 틀린다. §5.3 아티스트 상세의 `pass` 칸과
            **같은 판정**이라 두 화면이 다른 말을 하지 않는다.

            ### bannerImageUrl

            `artists.motif_bg_image_key` 를 공개 CDN URL 로 바꾼 값이고 미설정이면 `null`
            이다. **숍 공용 배너(§11.11)와는 별개**다.
            """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "성공 — 미보유면 prices, 보유 중이면 빈 배열",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "result": "SUCCESS",
                                      "code": "SUCCESS",
                                      "message": "요청이 성공했습니다.",
                                      "data": {
                                        "artistSeq": 3,
                                        "bannerImageUrl": "https://cdn.motif.io/artists/3f9c1a2b/motif-bg.jpg",
                                        "passActive": false,
                                        "expiresDttm": null,
                                        "prices": [
                                          { "durationMonths": 1, "amount": "9900.00", "currency": "KRW" },
                                          { "durationMonths": 3, "amount": "27900.00", "currency": "KRW" }
                                        ]
                                      },
                                      "timestamp": "2026-09-10T00:00:00.000Z"
                                    }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "미존재 · 삭제 아티스트",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = FailResponseSchema.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "result": "FAIL",
                                      "code": "ARTIST_NOT_FOUND",
                                      "message": "존재하지 않는 아티스트입니다.",
                                      "data": null,
                                      "timestamp": "2026-09-10T00:00:00.000Z"
                                    }""")))
    })
    ApiResponse<ArtistMotifPassResponse> motifPass(
            @Parameter(hidden = true) AuthenticatedUser user,
            @Parameter(description = "아티스트 seq", example = "3") Integer artistSeq);
}
