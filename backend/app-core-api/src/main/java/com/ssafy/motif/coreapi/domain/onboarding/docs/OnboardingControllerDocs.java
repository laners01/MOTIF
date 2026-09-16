package com.ssafy.motif.coreapi.domain.onboarding.docs;

import com.ssafy.motif.coreapi.domain.onboarding.dto.OnboardingRequest;
import com.ssafy.motif.coreapi.domain.onboarding.dto.OnboardingResponse;
import com.ssafy.motif.coreapi.global.docs.FailResponseSchema;
import com.ssafy.motif.coreapi.global.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 온보딩 API 문서 계약 (명세 §3.3).
 *
 * <p>Swagger 애너테이션만 여기 둔다 — 매핑과 {@code @Valid} 는 구현 클래스가 갖는다
 * (팀 표준, {@code backend/README.md}).
 *
 * <p><b>아래 예시의 닉네임 오류 문구는 {@link com.ssafy.motif.coreapi.global.validation.Nickname}
 * 가 소유한다.</b> 텍스트 블록 안이라 상수를 그대로 끼워 넣지 못해 글자를 옮겨 적었다 —
 * 규칙을 고치면 여기 예시도 같이 고쳐야 한다. 실제 응답을 만드는 것은 애너테이션 쪽이고,
 * 이 문서는 그 사본이다({@code §4.2} 예시도 마찬가지다).
 */
@Tag(name = "온보딩 (ONBOARDING)", description = "명세 §3 — 최초 로그인 후 구독·닉네임 확정")
public interface OnboardingControllerDocs {

    @Operation(
            summary = "온보딩 완료 (구독 일괄 + 닉네임 확정)",
            description = """
                    아티스트 구독과 닉네임을 **하나의 원자 트랜잭션**으로 확정한다 (명세 §3.3).

                    - **인증 필요.**
                    - 화면은 2단계(아티스트 선택 → 닉네임)지만 **제출은 마지막 1회**다.
                    - 아티스트가 하나라도 없거나 삭제됐으면 **아무것도 저장되지 않는다** — 닉네임도 바뀌지 않는다.
                    - `artistSeqs` 의 중복은 서버가 제거하며, `subscribedCount` 는 제거 후 개수다.
                    - 닉네임은 **중복을 허용한다**(C1 회의 확정). 형식만 검증한다 — 영문·한글·숫자·`.`·`_`, 2~20자.
                    - 온보딩 화면 노출 판정은 §2.1 로그인 응답의 `isNewUser` 가 유일한 근거다.
                      온보딩 도중 이탈 후 재로그인하면 `false` 가 되므로, **클라이언트가 완료 전 홈 진입을 막아야 한다.**

                    아티스트 목록은 §5.1(`GET /artists/ranking`)·§5.2(`GET /artists/search`)를 그대로 쓴다.

                    상세 명세: `backend/docs/api/detail/03-onboarding.md`
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "온보딩 완료",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = OnboardingApiResponseSchema.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "result": "SUCCESS",
                                      "code": "SUCCESS",
                                      "message": "요청이 성공했습니다.",
                                      "data": {
                                        "subscribedCount": 3,
                                        "nickname": "모티프러버"
                                      },
                                      "timestamp": "2026-09-10T00:00:00.000Z"
                                    }"""))),
            @ApiResponse(responseCode = "400", description = "빈 배열·닉네임 형식 위반",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = FailResponseSchema.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "result": "FAIL",
                                      "code": "INVALID_PARAMETER",
                                      "message": "요청 값이 올바르지 않습니다.",
                                      "data": [
                                        { "field": "artistSeqs", "message": "아티스트를 1명 이상 선택해야 합니다" },
                                        { "field": "nickname", "message": "2~20자의 영문·한글·숫자·마침표·언더바만 허용합니다." }
                                      ],
                                      "timestamp": "2026-09-10T00:00:00.000Z"
                                    }"""))),
            @ApiResponse(responseCode = "401", description = "access token 없음·만료·위조",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = FailResponseSchema.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "result": "FAIL",
                                      "code": "UNAUTHORIZED",
                                      "message": "로그인이 필요합니다.",
                                      "data": null,
                                      "timestamp": "2026-09-10T00:00:00.000Z"
                                    }"""))),
            @ApiResponse(responseCode = "404", description = "요청에 미존재·삭제 아티스트가 포함됨 — 목록을 다시 받아 선택시킨다",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = FailResponseSchema.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "result": "FAIL",
                                      "code": "ARTIST_NOT_FOUND",
                                      "message": "존재하지 않는 아티스트입니다.",
                                      "data": null,
                                      "timestamp": "2026-09-10T00:00:00.000Z"
                                    }"""))),
            @ApiResponse(responseCode = "409", description = "이미 온보딩을 마친 계정 — 클라이언트는 홈으로 보낸다",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = FailResponseSchema.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "result": "FAIL",
                                      "code": "ALREADY_ONBOARDED",
                                      "message": "이미 온보딩을 완료한 계정입니다.",
                                      "data": null,
                                      "timestamp": "2026-09-10T00:00:00.000Z"
                                    }""")))
    })
    com.ssafy.motif.common.response.ApiResponse<OnboardingResponse> complete(
            // 인증 필터가 채우는 principal 이라 요청 파라미터가 아니다 — 문서에서 감춘다
            @Parameter(hidden = true) AuthenticatedUser user,
            @Schema(description = "구독할 아티스트와 확정할 닉네임") OnboardingRequest request);
}
