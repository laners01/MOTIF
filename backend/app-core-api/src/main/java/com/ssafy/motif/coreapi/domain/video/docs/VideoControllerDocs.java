package com.ssafy.motif.coreapi.domain.video.docs;

import com.ssafy.motif.common.response.ApiResponse;
import com.ssafy.motif.coreapi.domain.video.dto.VideoItem;
import com.ssafy.motif.coreapi.domain.video.dto.VideoPublishRequest;
import com.ssafy.motif.coreapi.domain.video.dto.VideoPublishResponse;
import com.ssafy.motif.coreapi.domain.video.dto.VideoReportRequest;
import com.ssafy.motif.coreapi.domain.video.dto.VideoUpdateRequest;
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
 * 참여 영상 API 문서 계약 (명세 §8.2 · §8.5 · §8.6 · §8.7 · §8.9 · §8.10).
 *
 * <p>Swagger 애너테이션만 여기 둔다 — 매핑은 구현 클래스가 갖는다
 * (팀 표준, {@code backend/README.md}).
 */
@Tag(name = "참여 영상 (VIDEO)", description = "명세 §8 — 챌린지 참여 영상")
public interface VideoControllerDocs {

    @Operation(
            summary = "참여 영상 단건 (§8.2)",
            description = """
                    **이 응답이 "참여 영상 항목" 의 정의다** (명세 §8.2). §8.1 피드가 같은 형태를
                    배열로 내려주고, §6.1 HOT 같은 **경량 항목**을 쓰는 화면은 뷰어에 진입할 때
                    이 API 를 1회 호출해 오버레이 정보를 채운다.

                    - **인증 필요.** `isLiked`·`isMine` 이 호출자 기준이다.
                    - `videoUrl` 은 **마스터 재생목록(`master.m3u8`)** 이고 단일 MP4 가 아니다(§1.7).
                      참여 영상은 `visibility` 와 무관하게 보호 등급이라 CDN 절대 주소에
                      **프리픽스 서명**(`Policy`·`Signature`·`Key-Pair-Id`)이 붙는다(ADR-0012·0013).
                    - `isMine=true` 면 클라이언트가 Bottom Sheet 에 수정·삭제를 노출한다(§8.9·§8.10).
                    - **조회수는 올라가지 않는다.** 조회 이벤트는 §8.5 `POST /videos/view` 다.
                      읽기 API 가 쓰기를 겸하면 재시도·프리페치가 그대로 조회수가 된다.

                    ### 안 보이는 것은 전부 404 다

                    미존재 · 삭제된 영상 · **삭제된 사용자의 콘텐츠** · 삭제된 챌린지에 속한 영상 ·
                    **타인의 `PRIVATE`** 이 모두 같은 `VIDEO_NOT_FOUND` 다. 타인의 `PRIVATE` 을
                    403 으로 가르면 "그 영상은 존재한다" 가 응답에서 읽히기 때문이다(§1.8 존재 은닉).

                    **"관심 없음"(§8.6) 한 영상은 열린다** — 그 기능은 §8.1 피드에서 빼는 장치지
                    접근 차단이 아니다. 링크로 직접 들어오면 정상 응답이다.
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
                                "videoSeq": 88,
                                "challengeSeq": 11,
                                "description": "재밌었다!",
                                "videoUrl": "https://cdn.motif.io/private/challenge/3fa85f64-5717-4562-b3fc-2c963f66afa6/master.m3u8?Policy=eyJTdGF0ZW1lbnQiOlt7...&Signature=...&Key-Pair-Id=K27QE85H6DLYDD",
                                "thumbnailUrl": "https://cdn.motif.io/private/challenge/3fa85f64-5717-4562-b3fc-2c963f66afa6/thumb.jpg?Expires=1789000000&Signature=...&Key-Pair-Id=K27QE85H6DLYDD",
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
                              },
                              "timestamp": "2026-09-17T00:00:00.000Z"
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
                                      "timestamp": "2026-09-17T00:00:00.000Z"
                                    }""")))
    })
    ApiResponse<VideoItem> detail(
            @Parameter(hidden = true) AuthenticatedUser user,
            @Parameter(description = "참여 영상 seq", example = "88") Long videoSeq);

    @Operation(
            summary = "조회 이벤트 (§8.5)",
            description = """
                    영상이 재생되면 호출한다 (명세 §8.5). **여러 영상의 조회수를 묶어 보낸다** —
                    경로에 `{videoSeq}` 가 없다(2026-09-21 재정의 — ADR-0015).

                    ```json
                    { "views": { "101": 3, "102": 1, "115": 2 } }
                    ```

                    - **인증 필요.**
                    - **202 Accepted.** 집계는 비동기다 — "접수했다"는 뜻이고 `viewCount` 가 즉시
                      오르지 않는다. DB 반영은 **20분 주기 배치**가 한다.
                    - 클라이언트는 조회를 모아 두었다가 화면을 뜰 때나 일정 간격으로 한 번 보낸다.
                      건마다 왕복하면 재생 중에 네트워크를 계속 쓴다.

                    ### 제약 — 하나라도 어기면 요청 전체가 400 이다

                    | 항목 | 값 |
                    |---|---|
                    | `views` 항목 수 | 1 ~ 50 |
                    | 영상당 `count` | 1 ~ 100 |

                    **부분 수용하지 않는다.** 어긋난 항목만 조용히 버리면 클라이언트 버그가 응답에도
                    로그에도 남지 않는다. 어느 항목이 문제인지는 `data` 에 실린다.

                    ### 404 가 없다

                    존재하지 않는 `videoSeq` 를 보내도 **202** 다. 조회 이벤트마다 DB 를 확인하면
                    집계를 Redis 로 뺀 의미가 없다 — 없는 키는 배치의 `UPDATE` 가 0행으로 버리고,
                    랭킹 모집단은 DB 에서 나오므로 순위에도 오르지 않는다.

                    **Redis 장애도 202 다**(fail-open). 영상은 정상 재생되는데 조회수 집계 실패로
                    에러를 받는 것이 더 나쁘다. 서버는 로그만 남긴다.

                    ### 1회 인정 기준은 미결이다

                    같은 사용자의 중복 조회 · 최소 시청 시간 · 본인 영상 포함 여부(원본 §16 #3)가
                    정해지지 않아 **서버는 보낸 `count` 를 믿는다.** 정책이 정해져도 서버 내부에서만
                    반영하며 이 계약은 바뀌지 않는다.
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
                            schema = @Schema(implementation = FailResponseSchema.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "result": "FAIL",
                                      "code": "INVALID_PARAMETER",
                                      "message": "요청 값이 올바르지 않습니다.",
                                      "data": { "seq": 101, "maxCountPerTarget": 100 },
                                      "timestamp": "2026-09-21T00:00:00.000Z"
                                    }""")))
    })
    ApiResponse<Void> view(ViewRequest request);

    @Operation(
            summary = "관심 없음 (§8.6)",
            description = """
                    이후 **§8.1 피드에서 그 영상을 빼달라**는 요청이다 (명세 §8.6).
                    요청 본문이 없다.

                    - **인증 필요.**
                    - **멱등이다.** 이미 숨긴 영상을 다시 호출해도 200 이고, "이미 숨김" 을 알리는
                      코드는 없다 — 사용자가 원하는 상태가 같다.
                    - **해제 API 가 없다**(기본안). 한 번 숨기면 되돌리는 화면이 없다.
                    - **접근 차단이 아니다.** 숨긴 영상도 §8.2 단건 조회는 열린다 — 링크·공유로
                      직접 들어오는 길까지 막지 않는다.
                    - 안 보이는 영상은 숨길 대상도 아니다 — §8.2 와 **같은 404 규칙**이다
                      (삭제 · 삭제 사용자 · 삭제된 챌린지 소속 · **타인의 `PRIVATE`**).
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "숨김 (이미 숨긴 상태도 200)",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {
                              "result": "SUCCESS",
                              "code": "SUCCESS",
                              "message": "요청이 성공했습니다.",
                              "data": null,
                              "timestamp": "2026-09-17T00:00:00.000Z"
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
                                      "timestamp": "2026-09-17T00:00:00.000Z"
                                    }""")))
    })
    ApiResponse<Void> hide(
            @Parameter(hidden = true) AuthenticatedUser user,
            @Parameter(description = "참여 영상 seq", example = "88") Long videoSeq);

    @Operation(
            summary = "게시 정보 수정 (§8.9)",
            description = """
                    뷰어 Bottom Sheet 의 "수정". **부분 수정이라 보낸 필드만 반영한다** (명세 §8.9).

                    - **인증 필요. 본인 영상만 고칠 수 있다.**
                    - 고칠 수 있는 것은 `description` · `visibility` · `commentEnabled` 셋뿐이다.
                      제목은 개념 자체가 없다(R3 — 컬럼이 삭제됐다).
                    - **필드를 생략하면 그대로 둔다.** `description` 에 빈 문자열을 보내면 **지운다** —
                      생략과 다르다.
                    - 셋 다 생략하면 아무것도 바꾸지 않고 **현재 상태를 돌려준다**(200).
                    - 응답은 **§8.2 항목(수정 반영본)** 이다.

                    ### 404 가 403 보다 앞선다

                    남의 **`PRIVATE`** 영상에 보내면 403 이 아니라 **404** 다 — 403 을 주면 "그 영상이
                    존재한다"가 읽힌다(§1.8 존재 은닉). 403 은 **보이는데 내 것이 아닐 때**,
                    즉 남의 `PUBLIC` 영상에만 나간다.

                    ### visibility 를 바꿔도 videoUrl 은 그대로다

                    산출물은 게시 여부와 무관하게 `private/` 한 곳에 있고, `visibility` 는
                    **앱 안에서 누가 볼 자격이 있는가**만 정한다(ADR-0013). 키를 옮기지 않으므로
                    S3 복사·삭제도 CloudFront 무효화도 일어나지 않는다.

                    - **`PUBLIC → PRIVATE`**: 목록·조회에서는 즉시 사라진다. 재생은 **서명 갱신
                      TTL 안에** 닫힌다 — 만료되면 클라이언트가 `POST /media/{jobUuid}/signature`
                      를 거쳐야 하는데 거기서 인가를 다시 하기 때문이다. 이미 발급된 서명이
                      만료될 때까지(기본 10분)가 최대 지연이라 "즉시 차단"은 약속하지 않는다.
                    - **`PRIVATE → PUBLIC`**: 즉시 남에게도 보이고 재생된다. 남의 `PUBLIC` 영상에도
                      서명이 나가고 갱신도 받을 수 있다 — 인가는 `visibility` 로 하지 서명 유무로
                      하지 않는다.
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "수정됨 — data 는 §8.2 항목(수정 반영본)",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {
                              "result": "SUCCESS",
                              "code": "SUCCESS",
                              "message": "요청이 성공했습니다.",
                              "data": {
                                "videoSeq": 88,
                                "challengeSeq": 11,
                                "description": "수정한 문구",
                                "videoUrl": "https://cdn.motif.io/private/challenge/3fa85f64-5717-4562-b3fc-2c963f66afa6/master.m3u8?Policy=eyJTdGF0ZW1lbnQiOlt7...&Signature=...&Key-Pair-Id=K27QE85H6DLYDD",
                                "thumbnailUrl": "https://cdn.motif.io/private/challenge/3fa85f64-5717-4562-b3fc-2c963f66afa6/thumb.jpg?Expires=1789000000&Signature=...&Key-Pair-Id=K27QE85H6DLYDD",
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
                                "commentEnabled": false,
                                "visibility": "PRIVATE",
                                "isMine": true,
                                "regDttm": "2026-09-01T10:00:00.000Z"
                              },
                              "timestamp": "2026-09-17T00:00:00.000Z"
                            }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "description 1000자 초과 · visibility 가 PUBLIC|PRIVATE 이 아님",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = FailResponseSchema.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "result": "FAIL",
                                      "code": "INVALID_PARAMETER",
                                      "message": "요청 값이 올바르지 않습니다.",
                                      "data": null,
                                      "timestamp": "2026-09-17T00:00:00.000Z"
                                    }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403", description = "남의 PUBLIC 영상 (남의 PRIVATE 은 404 다)",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = FailResponseSchema.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "result": "FAIL",
                                      "code": "FORBIDDEN",
                                      "message": "권한이 없습니다.",
                                      "data": null,
                                      "timestamp": "2026-09-17T00:00:00.000Z"
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
                                      "timestamp": "2026-09-17T00:00:00.000Z"
                                    }""")))
    })
    ApiResponse<VideoItem> update(
            @Parameter(hidden = true) AuthenticatedUser user,
            @Parameter(description = "참여 영상 seq", example = "88") Long videoSeq,
            VideoUpdateRequest request);

    @Operation(
            summary = "게시 영상 삭제 (§8.10)",
            description = """
                    본인이 게시한 참여 영상을 내린다 (명세 §8.10). 요청 본문이 없다.

                    - **인증 필요. 본인 영상만 지울 수 있다.**
                    - **소프트 삭제다.** 행은 남고 비노출로 바뀐다 — §8.1 피드 · §8.2 단건 ·
                      §4.1/§4.8 내 그리드 · §6.1 HOT 에서 모두 사라진다.
                    - 삭제되면 그 챌린지의 **참여 수(`participantCount`)가 1 내려간다** — 처음
                      지울 때 한 번만이다.
                    - **이미 삭제된 영상은 404** 다(멱등 200 이 아니다). "없는 영상" 과 "이미 지운
                      영상" 을 §1.8 이 구분하지 않기 때문이다.
                    - **좋아요·댓글·신고 이력은 지우지 않는다.** 신고 이력은 어드민 처리 근거다.
                    - **산출물(S3)은 지우지 않는다.** 명세가 소프트 삭제만 정의한다.

                    ### 404 가 403 보다 앞선다

                    §8.9 와 같다 — 남의 **`PRIVATE`** 영상은 404, 남의 `PUBLIC` 영상이어야 403 이다.
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "삭제됨",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {
                              "result": "SUCCESS",
                              "code": "SUCCESS",
                              "message": "요청이 성공했습니다.",
                              "data": null,
                              "timestamp": "2026-09-17T00:00:00.000Z"
                            }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403", description = "남의 PUBLIC 영상 (남의 PRIVATE 은 404 다)",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = FailResponseSchema.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "result": "FAIL",
                                      "code": "FORBIDDEN",
                                      "message": "권한이 없습니다.",
                                      "data": null,
                                      "timestamp": "2026-09-17T00:00:00.000Z"
                                    }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "미존재 · **이미 삭제된 영상** · 삭제 사용자의 콘텐츠 · 타인의 PRIVATE · 삭제된 챌린지 소속",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = FailResponseSchema.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "result": "FAIL",
                                      "code": "VIDEO_NOT_FOUND",
                                      "message": "존재하지 않는 영상입니다.",
                                      "data": null,
                                      "timestamp": "2026-09-17T00:00:00.000Z"
                                    }""")))
    })
    ApiResponse<Void> delete(
            @Parameter(hidden = true) AuthenticatedUser user,
            @Parameter(description = "참여 영상 seq", example = "88") Long videoSeq);

    @Operation(
            summary = "영상 신고 (§8.7)",
            description = """
                    뷰어 Bottom Sheet 의 신고. 접수만 하고 **클라이언트는 다음 영상으로 이동**한다
                    (명세 §8.7).

                    - **인증 필요.**
                    - **1인 1대상 1회다.** 처리 결과와 무관하게 재신고는 409 다 — 기각된 신고도 그렇다.
                    - **접수는 제재가 아니다.** 신고했다고 그 영상이 숨겨지거나 목록에서 빠지지 않는다.
                      판단·조치는 어드민 몫이다.
                    - 안 보이는 영상은 신고 대상도 아니다 — §8.2 와 **같은 404 규칙**이다
                      (삭제 · 삭제 사용자 · 삭제된 챌린지 소속 · **타인의 `PRIVATE`**).

                    ### reasonCode 는 문자열이다

                    **코드표가 아직 정해지지 않았다**(명세 §16 #4 — §8.7·§9.6·§13.3 공통 미결).
                    지금 enum 으로 고정하면 표가 정해질 때 이미 쌓인 값이 열거에 없게 되므로,
                    서버는 **비어 있지 않은 30자 이내 문자열**만 확인한다. 클라이언트는 화면의
                    사유 목록에 대응하는 값을 보내면 된다(`SPAM` 같은 형태를 권한다).
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
                              "timestamp": "2026-09-17T00:00:00.000Z"
                            }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "reasonCode 누락·공백·30자 초과",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = FailResponseSchema.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "result": "FAIL",
                                      "code": "INVALID_PARAMETER",
                                      "message": "요청 값이 올바르지 않습니다.",
                                      "data": [
                                        { "field": "reasonCode", "message": "공백일 수 없습니다" }
                                      ],
                                      "timestamp": "2026-09-17T00:00:00.000Z"
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
                                      "timestamp": "2026-09-17T00:00:00.000Z"
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
                                      "timestamp": "2026-09-17T00:00:00.000Z"
                                    }""")))
    })
    ApiResponse<Void> report(
            @Parameter(hidden = true) AuthenticatedUser user,
            @Parameter(description = "참여 영상 seq", example = "88") Long videoSeq,
            VideoReportRequest request);

    String LIKE_DESCRIPTION = """
            참여 영상 좋아요 (§8.3).

            - **인증 필요.** 요청 본문 없음.
            - **성공은 `204 No Content` 이고 본문이 없다**(§1.2 봉투 예외). `isLiked`·
              `likeCount` 를 돌려주지 않으므로 클라이언트가 **낙관적으로 반영하고 실패 시
              롤백**한다 — §9.5 댓글 · §10.2 공지 좋아요와 같은 계약이다.
            - **멱등이다.** 이미 누른 상태에서 POST 를, 누르지 않은 상태에서 DELETE 를
              보내도 204 이고 `likeCount` 는 움직이지 않는다.
            - 해제는 **하드 삭제**다.

            ### 아티스트가 누르면 `hasArtistLike` 가 함께 움직인다

            그 영상이 속한 챌린지의 아티스트가 누르면 `has_artist_like` 가 켜지고 해제하면
            꺼진다. 이 값은 §8.1 피드 정렬에 반영되므로 좋아요 한 건의 문제가 아니다.

            ### 404 가 덮는 범위

            §8.2 단건과 **같은 판정**이다 — 미존재 · 삭제 영상 · 삭제된 사용자의 콘텐츠 ·
            삭제된 챌린지 소속 · **타인의 `PRIVATE`** 이 모두 `VIDEO_NOT_FOUND` 다.
            여기만 느슨하면 좋아요 응답이 타인의 비공개 영상 존재를 알려 주는 탐지 수단이
            된다(§1.8).
            """;

    @Operation(summary = "참여 영상 좋아요 (§8.3)", description = LIKE_DESCRIPTION)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "204", description = "반영됨 (본문 없음)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "미존재 · 비노출 영상",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = FailResponseSchema.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "result": "FAIL",
                                      "code": "VIDEO_NOT_FOUND",
                                      "message": "존재하지 않는 영상입니다.",
                                      "data": null,
                                      "timestamp": "2026-09-17T00:00:00.000Z"
                                    }""")))
    })
    void like(@Parameter(hidden = true) AuthenticatedUser user,
              @Parameter(description = "참여 영상 식별자", example = "9001") Long videoSeq);

    @Operation(summary = "참여 영상 좋아요 해제 (§8.3)", description = LIKE_DESCRIPTION)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "204", description = "반영됨 (본문 없음)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "미존재 · 비노출 영상",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = FailResponseSchema.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "result": "FAIL",
                                      "code": "VIDEO_NOT_FOUND",
                                      "message": "존재하지 않는 영상입니다.",
                                      "data": null,
                                      "timestamp": "2026-09-17T00:00:00.000Z"
                                    }""")))
    })
    void unlike(@Parameter(hidden = true) AuthenticatedUser user,
                @Parameter(description = "참여 영상 식별자", example = "9001") Long videoSeq);

    // ---------------------------------------------------------------------------------
    // 8.8 게시
    // ---------------------------------------------------------------------------------

    String PUBLISH_DESCRIPTION = """
            합성이 끝난 잡(§13)의 결과물을 **참여 영상으로 게시**한다.

            제목의 "업로드" 는 바이트가 아니라 **게시**다 — 촬영본도 합성 산출물도 §13.1
            presign 으로 이미 S3 에 있다. 이 API 는 그것을 가리키는 행을 만든다.

            - **인증 필요.** 본인 잡만 게시할 수 있다.
            - **`title` 은 받지 않는다** (R3 — 컬럼이 삭제됐다).
            - **201 Created.** 클라이언트는 `videoSeq` 로 전체 화면 뷰어(§8.2)로 이동한다.

            ### 서버 처리

            1. 요청자가 `role=ARTIST` 면 **403** — 아티스트 계정은 참여 영상을 게시할 수 없다
            2. 잡 검증 — `CHALLENGE` 타입 · `DONE` 상태 · 본인 소유 · 미게시
            3. 원본 챌린지가 삭제되지 않았는지 재확인 — 가공 중에 어드민이 지웠을 수 있다
            4. `participation_videos` 생성 — 영상·썸네일 키는 **잡 결과에서**, `artist_seq` 는
               **챌린지에서** 복사한다. `duration_ms` 는 `challenges.duration_ms` *(기본안 —
               잡에 길이 칸이 없다)*
            5. `challenges.participant_count + 1` — 같은 트랜잭션

            ### visibility 는 키를 움직이지 않는다

            **산출물은 게시 여부와 무관하게 `private/challenge/{jobUuid}/` 한 곳에 있다**
            (ADR-0013). `visibility` 는 *앱 안에서 누가 볼 자격이 있는가*만 정하고, 그 자격을
            CDN 에 전달하는 일은 **서명**이 맡는다. S3 복사도 CloudFront 무효화도 없다.

            `PUBLIC` 이든 `PRIVATE` 이든 재생 주소는 core-api 가 서명해 내주는 같은 경로다.
            전환(§8.9)도 DB 값 하나만 바뀐다.

            ### description

            상한 **1000자** *(기본안)* — §8.9 수정과 같은 값이다. DB 는 TEXT 라 무제한이고,
            상한이 없으면 본문 크기 제한이 서버 전역 설정에만 걸린다.
            """;

    String PUBLISH_REQUEST_EXAMPLE = """
            {
              "jobUuid": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
              "description": "재밌었다!",
              "visibility": "PUBLIC",
              "commentEnabled": true
            }""";

    String PUBLISH_CREATED_EXAMPLE = """
            {
              "result": "SUCCESS",
              "code": "SUCCESS",
              "message": "요청이 성공했습니다.",
              "data": { "videoSeq": 88 },
              "timestamp": "2026-09-10T00:00:00.000Z"
            }""";

    String PUBLISH_FORBIDDEN_EXAMPLE = """
            {
              "result": "FAIL",
              "code": "FORBIDDEN",
              "message": "권한이 없습니다.",
              "data": null,
              "timestamp": "2026-09-10T00:00:00.000Z"
            }""";

    String PUBLISH_CONFLICT_EXAMPLE = """
            {
              "result": "FAIL",
              "code": "ALREADY_PUBLISHED",
              "message": "이미 게시된 영상입니다.",
              "data": null,
              "timestamp": "2026-09-10T00:00:00.000Z"
            }""";

    @Operation(summary = "게시 — 합성 결과를 참여 영상으로 (§8.8)", description = PUBLISH_DESCRIPTION)
    @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = VideoPublishRequest.class),
                    examples = @ExampleObject(value = PUBLISH_REQUEST_EXAMPLE)))
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201", description = "게시됨",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = VideoPublishResponse.class),
                            examples = @ExampleObject(value = PUBLISH_CREATED_EXAMPLE))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403", description = "**ARTIST 계정의 게시 시도** (`FORBIDDEN`)",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = FailResponseSchema.class),
                            examples = @ExampleObject(value = PUBLISH_FORBIDDEN_EXAMPLE))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "잡 미존재 · 타인 잡 · MOTIF 잡 (`JOB_NOT_FOUND`)",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = FailResponseSchema.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "`JOB_NOT_DONE`(진행 중·실패) · `ALREADY_PUBLISHED` · `CHALLENGE_DELETED`",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = FailResponseSchema.class),
                            examples = @ExampleObject(value = PUBLISH_CONFLICT_EXAMPLE)))
    })
    ApiResponse<VideoPublishResponse> publish(
            @Parameter(hidden = true) AuthenticatedUser user,
            VideoPublishRequest request);

}
