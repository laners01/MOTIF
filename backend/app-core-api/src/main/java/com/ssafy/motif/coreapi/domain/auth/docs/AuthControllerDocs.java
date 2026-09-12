package com.ssafy.motif.coreapi.domain.auth.docs;

import com.ssafy.motif.coreapi.global.docs.FailResponseSchema;
import com.ssafy.motif.coreapi.domain.auth.dto.GoogleLoginRequest;
import com.ssafy.motif.coreapi.domain.auth.dto.LoginResponse;
import com.ssafy.motif.coreapi.domain.auth.dto.LogoutRequest;
import com.ssafy.motif.coreapi.domain.auth.dto.TokenRefreshRequest;
import com.ssafy.motif.coreapi.global.docs.SuccessNoDataApiResponseSchema;
import com.ssafy.motif.coreapi.global.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 인증 API 문서 계약 (명세 §2).
 *
 * <p><b>Swagger 애너테이션만 여기 둔다.</b> 컨트롤러에는 Spring MVC 매핑과 코드만
 * 남긴다 — 예시 JSON이 길어 본문 로직을 덮어버리기 때문이다.
 *
 * <p><b>매핑 애너테이션을 이 인터페이스에 올리지 않는다.</b> 인터페이스의
 * {@code @RequestMapping} 상속은 동작이 미묘해 매핑이 잡히지 않을 수 있다.
 * 여기는 문서 전용, 매핑·{@code @Valid}는 구현 클래스가 갖는다.
 *
 * <p>반환 타입의 {@code ApiResponse}는 우리 응답 봉투이고, 애너테이션의
 * {@code @ApiResponse}는 Swagger 것이다 — 이름이 겹쳐 봉투 쪽을 전체 경로로 쓴다.
 */
@Tag(name = "인증 (AUTH)", description = "명세 §2 — 로그인·토큰 재발급·로그아웃")
public interface AuthControllerDocs {

    @Operation(
            summary = "구글 로그인·자동 회원가입",
            description = """
                    구글 ID 토큰을 검증하고, 최초 로그인이면 users·user_accounts·user_oauth_accounts 를 생성한다 (명세 §2.1).

                    - 인증 불필요 (🔓)
                    - `isNewUser=true` 는 **이번 요청에서 users 행이 새로 생겼다**는 뜻이며, 온보딩 노출 판정의 유일한 근거다.
                      온보딩 도중 이탈 후 재로그인하면 `false` 가 되므로, 클라이언트가 온보딩 완료 전 홈 진입을 막아야 한다.
                    - 최초 가입 닉네임은 임시값 `user_{userSeq}` 다. 확정은 온보딩 완료 API(§3.3).
                    - `refreshToken` 은 **불투명 문자열**이다(JWT 아님). 해석하지 말고 받은 값을 그대로 보관·재전송한다.
                    """)
    @SecurityRequirements  // 🔓 — 전역 bearerAuth 요구를 이 오퍼레이션에서만 해제한다
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "로그인 성공",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = TokenApiResponseSchema.class),
                            examples = @ExampleObject(name = "신규 가입", value = """
                                    {
                                      "result": "SUCCESS",
                                      "code": "SUCCESS",
                                      "message": "요청이 성공했습니다.",
                                      "data": {
                                        "accessToken": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIiwicm9sZSI6IlVTRVIifQ.xxxxx",
                                        "refreshToken": "s7Fq2vXk9pLmNbVcZxAsDfGhJkLqWeRtYuIoP1234567",
                                        "tokenType": "Bearer",
                                        "expiresIn": 3600,
                                        "isNewUser": true,
                                        "user": { "userSeq": 1, "nickname": "user_1", "profileImageUrl": null }
                                      },
                                      "timestamp": "2026-09-10T00:00:00.000Z"
                                    }"""))),
            @ApiResponse(responseCode = "400", description = "`idToken` 누락·빈 문자열",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = FailResponseSchema.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "result": "FAIL",
                                      "code": "INVALID_PARAMETER",
                                      "message": "요청 값이 올바르지 않습니다.",
                                      "data": [{ "field": "idToken", "message": "idToken은 필수입니다" }],
                                      "timestamp": "2026-09-10T00:00:00.000Z"
                                    }"""))),
            @ApiResponse(responseCode = "401", description = "구글 ID 토큰 검증 실패 — 만료·위조·aud 불일치",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = FailResponseSchema.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "result": "FAIL",
                                      "code": "INVALID_GOOGLE_TOKEN",
                                      "message": "구글 인증에 실패했습니다. 다시 로그인해 주세요.",
                                      "data": null,
                                      "timestamp": "2026-09-10T00:00:00.000Z"
                                    }"""))),
            @ApiResponse(responseCode = "403", description = "정지(`ACCOUNT_SUSPENDED`)·삭제(`ACCOUNT_DELETED`) 계정",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = FailResponseSchema.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "result": "FAIL",
                                      "code": "ACCOUNT_SUSPENDED",
                                      "message": "정지된 계정입니다.",
                                      "data": null,
                                      "timestamp": "2026-09-10T00:00:00.000Z"
                                    }""")))
    })
    com.ssafy.motif.common.response.ApiResponse<LoginResponse> loginWithGoogle(
            @Schema(description = "구글 Sign-In 이 발급한 ID 토큰") GoogleLoginRequest request);

    @Operation(
            summary = "토큰 재발급 (rotation)",
            description = """
                    refresh token 으로 새 토큰 쌍을 발급한다 (명세 §2.2).

                    - 인증 불필요 (🔓) — access token 이 만료된 상태에서 호출하는 API 다. `Authorization` 헤더는 무시한다.
                    - **refresh token 은 1회용이다.** 이 호출이 성공하면 보낸 토큰은 폐기되고 새 토큰이 나온다.
                      클라이언트는 **받은 쌍으로 저장소를 교체**한다.
                    - 이미 폐기된 토큰이 다시 오면 **재사용(탈취 신호)** 으로 보고 401 이다.
                      미존재·만료·폐기·재사용 네 경우가 모두 같은 응답이다 — 사유는 알려주지 않는다.
                    - 401 을 받으면 로컬 토큰을 전부 버리고 로그인 화면으로 간다. **재시도하지 않는다.**
                    - 동시 요청은 클라이언트가 **단일 큐(mutex)로 직렬화**한다. 여럿이 각자 재발급하면 하나만 성공하고
                      나머지는 401 을 받아 불필요한 로그아웃이 일어난다.
                    - 응답 형태는 §2.1 과 같다. `isNewUser` 는 재발급에서 의미가 없어 항상 `false` 다.
                    - 다른 기기의 토큰은 영향받지 않는다 (`refresh_tokens` 는 기기별 행).

                    상세 명세: `backend/docs/api/detail/02-auth.md`
                    """)
    @SecurityRequirements  // 🔓
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "재발급 성공 — 보낸 refresh token 은 이 시점에 폐기됐다",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = TokenApiResponseSchema.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "result": "SUCCESS",
                                      "code": "SUCCESS",
                                      "message": "요청이 성공했습니다.",
                                      "data": {
                                        "accessToken": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIiwicm9sZSI6IlVTRVIifQ.yyyyy",
                                        "refreshToken": "Zr3Nk8dQpWxE2uYtA5bHvCmLs9fJgKo0987654321",
                                        "tokenType": "Bearer",
                                        "expiresIn": 3600,
                                        "isNewUser": false,
                                        "user": {
                                          "userSeq": 1,
                                          "nickname": "모티프러버",
                                          "profileImageUrl": "https://cdn.motif.io/profiles/3f9c1a2b.jpg"
                                        }
                                      },
                                      "timestamp": "2026-09-10T00:00:00.000Z"
                                    }"""))),
            @ApiResponse(responseCode = "400", description = "`refreshToken` 누락·빈 문자열",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = FailResponseSchema.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "result": "FAIL",
                                      "code": "INVALID_PARAMETER",
                                      "message": "요청 값이 올바르지 않습니다.",
                                      "data": [{ "field": "refreshToken", "message": "refreshToken은 필수입니다" }],
                                      "timestamp": "2026-09-10T00:00:00.000Z"
                                    }"""))),
            @ApiResponse(responseCode = "401",
                    description = "미존재·만료·폐기·**재사용 감지** — 네 경우 모두 이 응답이다. 로컬 토큰을 버리고 재로그인",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = FailResponseSchema.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "result": "FAIL",
                                      "code": "INVALID_REFRESH_TOKEN",
                                      "message": "다시 로그인해 주세요.",
                                      "data": null,
                                      "timestamp": "2026-09-10T00:00:00.000Z"
                                    }"""))),
            @ApiResponse(responseCode = "403", description = "재발급 시점 계정 상태 재검증 실패 — 정지·삭제 계정 (명세 §1.5)",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = FailResponseSchema.class),
                            examples = {
                                    @ExampleObject(name = "정지 계정", value = """
                                            {
                                              "result": "FAIL",
                                              "code": "ACCOUNT_SUSPENDED",
                                              "message": "정지된 계정입니다.",
                                              "data": null,
                                              "timestamp": "2026-09-10T00:00:00.000Z"
                                            }"""),
                                    @ExampleObject(name = "삭제 계정", value = """
                                            {
                                              "result": "FAIL",
                                              "code": "ACCOUNT_DELETED",
                                              "message": "삭제된 계정입니다.",
                                              "data": null,
                                              "timestamp": "2026-09-10T00:00:00.000Z"
                                            }""")
                            }))
    })
    com.ssafy.motif.common.response.ApiResponse<LoginResponse> refresh(
            @Schema(description = "재발급에 쓸 refresh token") TokenRefreshRequest request);

    @Operation(
            summary = "로그아웃 (현재 기기)",
            description = """
                    보낸 refresh token 하나만 폐기한다 (명세 §2.3).

                    - **인증 필요** — 우측 상단 Authorize 로 accessToken 을 넣고 호출한다.
                    - **현재 기기만** 로그아웃된다. 다른 기기의 refresh token 은 그대로 살아 있다.
                    - **멱등하다.** 미존재·이미 폐기·만료·타인 소유 토큰을 보내도 200 이다 —
                      어느 경우인지 알려주면 토큰 존재 여부가 새어 나가기 때문이다.
                    - **access token 은 무효화되지 않는다.** JWT 라 서버에 상태가 없어 만료(기본 1시간)까지 유효하다.
                      **클라이언트가 로컬에서 반드시 폐기**해야 한다.
                    - **푸시는 이 API 로 멈추지 않는다.** FCM 디바이스 토큰 비활성화(§14.2
                      `DELETE /api/v1/notifications/device-tokens`)를 **따로** 호출해야 한다.

                    클라이언트 순서: ① 이 API → ② §14.2 디바이스 토큰 비활성화 → ③ 로컬 토큰 폐기

                    상세 명세: `backend/docs/api/detail/02-auth.md`
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "로그아웃 성공 — 폐기할 대상이 없었어도 200 이다",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = SuccessNoDataApiResponseSchema.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "result": "SUCCESS",
                                      "code": "SUCCESS",
                                      "message": "요청이 성공했습니다.",
                                      "data": null,
                                      "timestamp": "2026-09-10T00:00:00.000Z"
                                    }"""))),
            @ApiResponse(responseCode = "400", description = "`refreshToken` 누락·빈 문자열",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = FailResponseSchema.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "result": "FAIL",
                                      "code": "INVALID_PARAMETER",
                                      "message": "요청 값이 올바르지 않습니다.",
                                      "data": [{ "field": "refreshToken", "message": "refreshToken은 필수입니다" }],
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
                                    }""")))
    })
    com.ssafy.motif.common.response.ApiResponse<Void> logout(
            // 인증 필터가 채우는 principal 이라 요청 파라미터가 아니다 — 문서에서 감춘다
            @Parameter(hidden = true) AuthenticatedUser user,
            @Schema(description = "폐기할 refresh token") LogoutRequest request);
}
