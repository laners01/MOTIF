package com.ssafy.motif.coreapi.domain.video.docs;

import com.ssafy.motif.common.response.ApiResponse;
import com.ssafy.motif.coreapi.domain.video.dto.VideoItem;
import com.ssafy.motif.coreapi.domain.video.dto.VideoReportRequest;
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
 * 참여 영상 API 문서 계약 (명세 §8.2 · §8.6 · §8.7).
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
                      공개 사본이 준비된 영상은 무서명 CloudFront URL, 내 `PRIVATE` 은 **core-api 주소**다.
                    - `isMine=true` 면 클라이언트가 Bottom Sheet 에 수정·삭제를 노출한다(§8.9·§8.10).
                    - **조회수는 올라가지 않는다.** 조회 이벤트는 §8.5 `POST /videos/{videoSeq}/view` 다.

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
                                "videoUrl": "https://cdn.motif.io/public/challenge/3fa85f64-5717-4562-b3fc-2c963f66afa6/master.m3u8",
                                "thumbnailUrl": "https://cdn.motif.io/public/challenge/3fa85f64-5717-4562-b3fc-2c963f66afa6/thumb.jpg",
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
                                      "message": "잘못된 요청 값입니다.",
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
}
