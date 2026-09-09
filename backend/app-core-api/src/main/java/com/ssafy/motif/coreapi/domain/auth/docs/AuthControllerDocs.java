package com.ssafy.motif.coreapi.domain.auth.docs;

import com.ssafy.motif.coreapi.global.docs.FailResponseSchema;
import com.ssafy.motif.coreapi.domain.auth.dto.GoogleLoginRequest;
import com.ssafy.motif.coreapi.domain.auth.dto.LoginResponse;
import io.swagger.v3.oas.annotations.Operation;
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
                            schema = @Schema(implementation = LoginApiResponseSchema.class),
                            examples = @ExampleObject(name = "신규 가입", value = """
                                    {
                                      "result": "SUCCESS",
                                      "code": null,
                                      "message": null,
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
                                      "code": "C001",
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
}
