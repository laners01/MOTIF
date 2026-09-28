package com.ssafy.motif.coreapi.domain.notification.docs;

import com.ssafy.motif.common.response.ApiResponse;
import com.ssafy.motif.coreapi.domain.notification.dto.DeviceTokenRequest;
import com.ssafy.motif.coreapi.global.docs.FailResponseSchema;
import com.ssafy.motif.coreapi.global.docs.SuccessNoDataApiResponseSchema;
import com.ssafy.motif.coreapi.global.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * FCM 디바이스 토큰 API 문서 계약 (명세 §14.1 · §14.2).
 *
 * <p>Swagger 애너테이션만 여기 둔다 — 매핑과 {@code @Valid} 는 구현 클래스가 갖는다
 * (팀 표준, {@code backend/README.md}).
 *
 * <p>예시 JSON 은 상세 명세 {@code docs/api/detail/14-notification.md} 와 <b>같은 값</b>이다.
 */
@Tag(name = "알림 (NOTIFICATION)", description = "명세 §14 — FCM 디바이스 토큰 (알림 목록·설정은 Android 로컬)")
public interface DeviceTokenControllerDocs {

    String SUCCESS_EXAMPLE = """
            {
              "result": "SUCCESS",
              "code": "SUCCESS",
              "message": "요청이 성공했습니다.",
              "data": null,
              "timestamp": "2026-09-10T00:00:00.000Z"
            }""";

    String INVALID_EXAMPLE = """
            {
              "result": "FAIL",
              "code": "INVALID_PARAMETER",
              "message": "요청 값이 올바르지 않습니다.",
              "data": [
                { "field": "fcmToken", "message": "fcmToken 은 필수다" }
              ],
              "timestamp": "2026-09-10T00:00:00.000Z"
            }""";

    @Operation(
            summary = "FCM 토큰 등록·재할당 (§14.1)",
            description = """
                    로그인 직후와 **FCM 토큰 갱신 시** 호출한다.

                    - **인증 필요.**
                    - `platform` 은 `ANDROID` | `IOS` | `WEB` 이고 **생략하면 `ANDROID`** 다
                      (DB CHECK·기본값과 같은 값이다).

                    ### upsert + 재할당 — `fcm_token` 이 전역 UNIQUE 라 행 하나가 기기 하나다

                    | 상황 | 동작 |
                    |---|---|
                    | 처음 보는 토큰 | 새 행 생성, `is_active=TRUE` |
                    | 같은 사용자의 기존 토큰 | `is_active=TRUE` 로 갱신(**멱등**) |
                    | **다른 사용자가 쓰던 토큰** | **현재 사용자로 재할당** + `is_active=TRUE` |

                    재할당이 필요한 이유는 **기기 하나를 여러 계정이 쓰는 경우**다. A 가 로그아웃하고
                    B 가 로그인하면 같은 FCM 토큰이 B 의 것이 돼야 한다 — 그러지 않으면 **B 의 알림이
                    A 에게 간다.**

                    ### 서버는 알림을 저장하지 않는다

                    `notifications` 테이블도 알림 목록 API 도 없다(§14.3). 서버는 **발송만** 하고
                    목록·읽음·삭제·정렬·ON/OFF 설정은 전부 **Android 로컬**이 맡는다. 그래서 재설치·
                    기기 변경 시 알림 이력은 복구되지 않는다.

                    발송은 전부 **data-only** 메시지다 — notification 페이로드를 쓰면 앱이 백그라운드일
                    때 시스템이 바로 띄워 **사용자의 OFF 설정을 무시**하게 된다.

                    ### 실패

                    **도메인 분기가 없다.** 공통 분기(401 등)와 형식 검증 400 뿐이다.
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "등록됨 (이미 등록된 토큰도 200)",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = SuccessNoDataApiResponseSchema.class),
                            examples = @ExampleObject(value = SUCCESS_EXAMPLE))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "`fcmToken` 누락·공백 · `platform` 이 ANDROID·IOS·WEB 이 아님",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = FailResponseSchema.class),
                            examples = @ExampleObject(value = INVALID_EXAMPLE)))
    })
    ApiResponse<Void> register(
            @Parameter(hidden = true) AuthenticatedUser user,
            @Schema(description = "FCM 등록 토큰과 플랫폼") DeviceTokenRequest request);

    @Operation(
            summary = "FCM 토큰 해제 (§14.2)",
            description = """
                    로그아웃할 때 **현재 기기 토큰만** 비활성화한다.

                    - **인증 필요.** 요청에 `platform` 은 쓰지 않는다 — 토큰 하나로 행이 특정된다.
                    - **멱등하다.** 등록되지 않은 토큰을 보내도 200 이다.
                    - **행을 지우지 않고 `is_active` 를 내린다** — 같은 기기로 다시 로그인하면
                      §14.1 이 되살린다.

                    ### 로그아웃은 2콜이다

                    **§2.3 로그아웃이 이 API 를 대신 불러 주지 않는다.** 코어 API 가 알림 서비스를
                    HTTP 로 부르지 않는다는 원칙 때문이며, 클라이언트가 순서대로 호출해야 한다.

                    ```
                    ① POST   /api/v1/auth/logout                    refresh token 폐기
                    ② DELETE /api/v1/notifications/device-tokens    푸시 중단
                    ③ 로컬 토큰 폐기
                    ```

                    **②를 빠뜨리면 로그아웃한 기기로 푸시가 계속 간다.**

                    ### 실패

                    **도메인 분기가 없다.**
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "비활성화됨 (미등록 토큰도 200 — 멱등)",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = SuccessNoDataApiResponseSchema.class),
                            examples = @ExampleObject(value = SUCCESS_EXAMPLE))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "`fcmToken` 누락·공백",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = FailResponseSchema.class),
                            examples = @ExampleObject(value = INVALID_EXAMPLE)))
    })
    ApiResponse<Void> deactivate(
            @Parameter(hidden = true) AuthenticatedUser user,
            @Schema(description = "해제할 FCM 등록 토큰") DeviceTokenRequest request);
}
