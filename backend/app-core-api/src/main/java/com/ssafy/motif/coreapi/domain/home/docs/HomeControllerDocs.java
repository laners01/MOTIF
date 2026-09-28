package com.ssafy.motif.coreapi.domain.home.docs;

import com.ssafy.motif.common.response.ApiResponse;
import com.ssafy.motif.common.response.CursorResponse;
import com.ssafy.motif.common.response.PageResponse;
import com.ssafy.motif.coreapi.domain.home.dto.ActivityItem;
import com.ssafy.motif.coreapi.domain.home.dto.HotVideoItem;
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
 * 홈 API 문서 계약 (명세 §6).
 *
 * <p>Swagger 애너테이션만 여기 둔다 — 매핑은 구현 클래스가 갖는다
 * (팀 표준, {@code backend/README.md}).
 */
@Tag(name = "홈 (HOME)", description = "명세 §6 — 홈 화면 섹션")
public interface HomeControllerDocs {

    @Operation(
            summary = "실시간 인기 참여 영상 HOT (§6.1)",
            description = """
                    홈 상단 가로 캐러셀과 클릭 후 세로 뷰어가 **함께 쓰는 하나의 목록**이다 (명세 §6.1).
                    **커서 페이지네이션**(§1.9).

                    - **인증 필요.**
                    - `cursor` 를 생략하면 현재 순위의 1위부터다. 응답의 `nextCursor` 를 그대로 다시 넣어
                      다음 장을 받는다. `hasNext=false`(= `nextCursor=null`)면 끝이다.
                    - **커서는 서버가 만든 opaque 토큰이다.** 해석·조작하지 마라. 망가진 토큰은
                      400 `INVALID_PARAMETER` 다.
                    - `size` 기본 10, 최대 100.

                    ### 순서는 20분마다 만들어지는 스냅샷이다 *(2026-09-21 재정의 — ADR-0015)*

                    ```
                    tScore = log10(1 + totalViews)    / log10(1 + T0)
                    rScore = log10(1 + recentViews1h) / log10(1 + R0)
                    dRel   = (recentViews1h − previousViews1h) / (previousViews1h + K)
                    dScore = 0.5 + 0.5 × clamp(dRel / D0, −1, +1)

                    score  = Wt·tScore + Wr·rScore + Wd·dScore      (기본 0.3 / 0.5 / 0.2)
                    ```

                    누적 규모 · 최근 1시간 규모 · 모멘텀 세 항이며 순위는 배치가
                    `score DESC → videoSeq ASC` 로 **확정해 저장**한다. `sort` 파라미터가 없는 이유다.

                    - **시간 감쇠가 없다.** 게시가 오래됐다는 이유만으로 영원히 감점되던 것을
                      걷어냈다 — 오래된 영상도 지금 뜨면 1위에 오른다.
                    - **좋아요·댓글 수는 점수에 넣지 않는다.** 응답에도 싣지 않는다(경량 항목).
                    - 정규화 기준은 그 틱의 최대값이 아니라 **고정 상수**(`T0`·`R0`·`D0`)다.
                      최대값으로 나누면 다른 영상의 등락이 내 순위를 바꿔 `rankDelta` 에 거짓
                      변동이 뜬다. 값은 `motif.view.video-ranking.*` 설정이다.
                    - 스냅샷에는 점수 상위 **500건**(`keep`)만 담는다.

                    **커서에 스냅샷 버전이 박혀 있다.** 진행 중인 스크롤은 배치가 순위를 갈아도 같은
                    순서를 계속 본다. 새 순위는 **커서 없이 다시 호출**할 때(당겨서 새로고침) 시작된다.
                    지난 버전은 **2시간**간 보존하므로, 그보다 오래 방치된 커서로 요청하면
                    **빈 목록 + `hasNext:false`** 다(오류가 아니다). 보존이 15분에서 늘어난 것은
                    커서 사정이 아니라 `rankDelta` 때문이다 — 대비 대상이 3틱(=1시간) 전 버전이라
                    그 키가 살아 있어야 하고, 짧으면 변동값이 조용히 전원 `null` 이 된다.

                    ### 캐러셀 → 뷰어 (R4)

                    뷰어용 API 가 따로 없다. n번째 카드를 누르면 **이미 받은 목록 + `nextCursor` +
                    클릭 인덱스**를 뷰어에 넘기고(공유 ViewModel), 뷰어는 스와이프로 순회하다 목록이
                    소진되면 같은 커서로 이어 받는다. 재조회·중복 로드가 없다.

                    ### 경량 항목이다

                    좋아요·댓글 수와 `isLiked` 는 **없다**(회의 확정 R2). 뷰어에 들어갈 때 §8.2 단건
                    조회 1회로 받는다. 여기에는 클릭 즉시 재생을 시작할 최소 정보만 담는다.

                    - `videoUrl` 은 **마스터 재생목록(`master.m3u8`)** 이다 (§1.7, ADR-0010 D2).
                      참여 영상은 전부 보호 등급이라 **프리픽스 서명**이 붙는다(ADR-0012·0013) —
                      남의 `PUBLIC` 영상이어도 그렇다.
                    - `rank` 는 스냅샷 순위다. 스냅샷이 만들어진 뒤 삭제·비공개로 바뀐 영상과 요청자가
                      **"관심 없음"(§8.6)** 한 영상은 목록에서 빠지므로 **번호가 건너뛸 수 있다** —
                      다시 매기지 않는다(다음 페이지와 어긋난다). 그래서 한 페이지의 항목 수가 요청한
                      `size` 보다 적을 수 있고, 같은 커서라도 사람마다 다를 수 있다.
                    - `rankDelta` 는 **직전 순위 − 현재 순위**다. 상승이 양수(5위→3위 = `+2`),
                      하락이 음수, **신규 진입은 `null`** 이다(§5.1 과 같은 규약). 대비 구간은
                      **1시간**이고 값은 배치가 적재 시점에 계산해 둔 것이다.
                    - 순위 배치가 아직 한 번도 돌지 않았거나 후보가 없으면 **빈 목록 200** 이다.
                      **조회가 한 번도 없던 영상은 후보가 아니다**(`view_count > 0` — ADR-0015).
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
                                    "rank": 1,
                                    "rankDelta": 2,
                                    "videoSeq": 88,
                                    "videoUrl": "https://cdn.motif.io/private/challenge/3fa85f64-5717-4562-b3fc-2c963f66afa6/master.m3u8?Policy=eyJTdGF0ZW1lbnQiOlt7...&Signature=...&Key-Pair-Id=K27QE85H6DLYDD",
                                    "thumbnailUrl": "https://cdn.motif.io/private/challenge/3fa85f64-5717-4562-b3fc-2c963f66afa6/thumb.jpg?Expires=1789000000&Signature=...&Key-Pair-Id=K27QE85H6DLYDD",
                                    "durationMs": 15000,
                                    "song": { "title": "bailar toda la noche", "singerName": "mochico" },
                                    "author": { "userSeq": 5, "nickname": "팬123" },
                                    "viewCount": 3400
                                  }
                                ],
                                "prevCursor": null,
                                "nextCursor": "MTc4ODk5ODQwMDoxMA",
                                "hasPrevious": false,
                                "hasNext": true
                              },
                              "timestamp": "2026-09-10T00:00:00.000Z"
                            }
                            """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "cursor 토큰이 잘못됨 (`INVALID_PARAMETER`)",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = FailResponseSchema.class)))
    })
    ApiResponse<CursorResponse<HotVideoItem>> hotVideos(
            @Parameter(hidden = true) AuthenticatedUser user,
            @Parameter(description = "이전 응답의 nextCursor. 생략하면 현재 순위의 1위부터") String cursor,
            @Parameter(description = "페이지 크기 (기본 10, 최대 100)") int size);

    @Operation(
            summary = "구독 아티스트 최신 활동 (§6.3)",
            description = """
                    구독 중인 아티스트의 **신규 모티프 에셋 등록**과 **공지**를 한 목록에 섞어
                    등록 시각 통합 최신순으로 내린다 (명세 §6.3). **offset 페이지네이션**.

                    - **인증 필요.** 구독 목록과 `notice.isLiked` 가 호출자 기준이다.
                    - `size` 기본 20, 최대 100. 서버 고정 정렬이라 `sort` 를 받지 않는다.
                    - **구독이 0건이면 빈 목록 200** 이다(오류가 아니다).

                    ### 이종 항목이다 — `type` 으로 분기한다

                    | `type` | 채워지는 칸 |
                    |---|---|
                    | `MOTIF_ASSET` | `artist` + `asset` |
                    | `NOTICE` | `artist` + `notice` |

                    **쓰지 않는 칸은 응답에서 빠진다.** `MOTIF_ASSET` 항목에 `"notice": null` 이
                    실리지 않으므로 `type` 하나로만 분기하면 된다.

                    - `artist` 는 **두 타입이 같은 모양**이다(공통 아티스트 요약 — `groupName` 포함,
                      솔로 아티스트는 `null`).
                    - `asset` 에는 **좋아요·댓글 수가 없다** — 에셋에 그 기능 자체가 없다.
                      `asset.title` 은 `motif_assets.title` 전용 컬럼이며 소개 문구(`description`)와
                      다른 값이다. 클릭하면 §11.2 에셋 상세로 간다.
                    - `notice` 는 **본문 전체 + 이미지 전체 배열**(`displayOrder` 순)에 `isLiked` 까지
                      싣는다. 공지 상세 API 가 없어(구 §10.1 삭제) **이 항목이 공지 화면의 진입
                      데이터**이기 때문이다. §5.5 목록 항목과 같은 필드 구성이다.

                    ### 모집단과 정렬

                    구독 중인 **비삭제** 아티스트의 활성 에셋(`is_active=TRUE`)과 비삭제 공지다.
                    판매가 중단된 에셋을 남기면 눌렀을 때 상세가 막히는 카드가 생긴다.

                    정렬은 `regDttm DESC, type ASC, seq DESC` 로 고정이다 — 보조 키가 없으면 같은
                    초에 등록된 항목이 페이지마다 자리를 바꿔 중복·누락이 난다.
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
                                    "type": "MOTIF_ASSET",
                                    "regDttm": "2026-09-02T05:00:00.000Z",
                                    "artist": {
                                      "artistSeq": 3,
                                      "name": "카리나",
                                      "groupName": "aespa",
                                      "profileImageUrl": "https://cdn.motif.io/profiles/3f9c1a2b.jpg"
                                    },
                                    "asset": {
                                      "assetSeq": 21,
                                      "title": "9월 신규 에셋",
                                      "thumbnailUrl": "https://cdn.motif.io/assets/2b7f1c93.jpg",
                                      "durationMs": 9800
                                    }
                                  },
                                  {
                                    "type": "NOTICE",
                                    "regDttm": "2026-09-02T04:00:00.000Z",
                                    "artist": {
                                      "artistSeq": 7,
                                      "name": "윈터",
                                      "groupName": "aespa",
                                      "profileImageUrl": "https://cdn.motif.io/profiles/c4a7e9d2.jpg"
                                    },
                                    "notice": {
                                      "noticeSeq": 5,
                                      "content": "컴백 소식 전체 본문...",
                                      "images": [
                                        { "imageUrl": "https://cdn.motif.io/notices/9f3b2a71.jpg", "displayOrder": 0 }
                                      ],
                                      "likeCount": 200,
                                      "commentCount": 14,
                                      "isLiked": false
                                    }
                                  }
                                ],
                                "page": 0,
                                "size": 20,
                                "totalElements": 37,
                                "totalPages": 2,
                                "hasNext": true,
                                "hasPrevious": false
                              },
                              "timestamp": "2026-09-10T00:00:00.000Z"
                            }
                            """)))
    })
    ApiResponse<PageResponse<ActivityItem>> activities(
            @Parameter(hidden = true) AuthenticatedUser user,
            @Parameter(description = "0-base 페이지 번호 (기본 0)") int page,
            @Parameter(description = "페이지 크기 (기본 20, 최대 100)") int size);
}
