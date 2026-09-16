package com.ssafy.motif.coreapi.domain.home.docs;

import com.ssafy.motif.common.response.ApiResponse;
import com.ssafy.motif.common.response.CursorResponse;
import com.ssafy.motif.coreapi.domain.home.dto.HotVideoItem;
import com.ssafy.motif.coreapi.global.docs.FailResponseSchema;
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

                    ### 순서는 5분마다 만들어지는 스냅샷이다

                    `score = view_count × 0.5 ^ (age / 반감기)` 로 매긴 순위를 서버가 미리 확정해 둔다
                    (§6.1 랭킹 기본안 — 좋아요는 점수에 넣지 않는다). `sort` 파라미터가 없는 이유다.

                    **커서에 스냅샷 버전이 박혀 있다.** 진행 중인 스크롤은 배치가 순위를 갈아도 같은
                    순서를 계속 본다. 새 순위는 **커서 없이 다시 호출**할 때(당겨서 새로고침) 시작된다.
                    지난 버전은 **15분**간만 보존하므로, 그보다 오래 방치된 커서로 요청하면
                    **빈 목록 + `hasNext:false`** 다(오류가 아니다).

                    ### 캐러셀 → 뷰어 (R4)

                    뷰어용 API 가 따로 없다. n번째 카드를 누르면 **이미 받은 목록 + `nextCursor` +
                    클릭 인덱스**를 뷰어에 넘기고(공유 ViewModel), 뷰어는 스와이프로 순회하다 목록이
                    소진되면 같은 커서로 이어 받는다. 재조회·중복 로드가 없다.

                    ### 경량 항목이다

                    좋아요·댓글 수와 `isLiked` 는 **없다**(회의 확정 R2). 뷰어에 들어갈 때 §8.2 단건
                    조회 1회로 받는다. 여기에는 클릭 즉시 재생을 시작할 최소 정보만 담는다.

                    - `videoUrl` 은 **마스터 재생목록(`master.m3u8`)** 이다 (§1.7, ADR-0010 D2).
                      HOT 에는 **공개 사본이 준비된 PUBLIC 영상만** 오르므로 서명 없는 CloudFront URL 이다.
                    - `rank` 는 스냅샷 순위다. 스냅샷이 만들어진 뒤 삭제·비공개로 바뀐 영상은 목록에서
                      빠지므로 **번호가 건너뛸 수 있다** — 다시 매기지 않는다(다음 페이지와 어긋난다).
                    - 순위 배치가 아직 한 번도 돌지 않았거나 후보가 없으면 **빈 목록 200** 이다.
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
                                    "videoSeq": 88,
                                    "videoUrl": "https://cdn.motif.io/public/challenge/3fa85f64-5717-4562-b3fc-2c963f66afa6/master.m3u8",
                                    "thumbnailUrl": "https://cdn.motif.io/public/challenge/3fa85f64-5717-4562-b3fc-2c963f66afa6/thumb.jpg",
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
            @Parameter(description = "이전 응답의 nextCursor. 생략하면 현재 순위의 1위부터") String cursor,
            @Parameter(description = "페이지 크기 (기본 10, 최대 100)") int size);
}
