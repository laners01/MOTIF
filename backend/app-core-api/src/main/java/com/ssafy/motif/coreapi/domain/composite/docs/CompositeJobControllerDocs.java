package com.ssafy.motif.coreapi.domain.composite.docs;

import com.ssafy.motif.common.response.ApiResponse;
import com.ssafy.motif.coreapi.domain.composite.dto.CompositeJobCreateRequest;
import com.ssafy.motif.coreapi.domain.composite.dto.CompositeJobCreateResponse;
import com.ssafy.motif.coreapi.domain.composite.dto.CompositeJobRequestBody;
import com.ssafy.motif.coreapi.domain.composite.dto.CompositeJobRequestResponse;
import com.ssafy.motif.coreapi.domain.composite.dto.CompositeJobRetryResponse;
import com.ssafy.motif.coreapi.domain.composite.dto.CompositeJobStatusResponse;
import com.ssafy.motif.coreapi.global.docs.FailResponseSchema;
import com.ssafy.motif.coreapi.global.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.UUID;

/**
 * 합성 파이프라인 API 문서 계약 (명세 §13 — 13.1 · 13.2 · 13.3 · 13.4).
 *
 * <p>Swagger 애너테이션만 여기 둔다 — 매핑은 구현 클래스가 갖는다
 * (팀 표준, {@code backend/README.md}).
 *
 * <p>예시 JSON 은 상세 명세 {@code docs/api/detail/13-composite.md} 와 <b>같은 값</b>이다.
 */
@Tag(name = "합성 (COMPOSITE)", description = "명세 §13 — 챌린지·모티프 공용 합성 파이프라인")
public interface CompositeJobControllerDocs {

    String CREATE_DESCRIPTION = """
            합성 파이프라인의 **1단계**다. 잡 행을 `UPLOADED` 로 만들고 촬영 원본을 올릴
            **presigned PUT URL** 을 돌려준다. 다음은 그 URL 로 업로드 → **§13.2 합성 요청**이다.

            ```
            ① POST /composite-jobs     ← 지금 이 API. 잡 생성 + 업로드 URL 발급
            ② PUT {uploadUrl}          클라이언트가 S3 로 직행 업로드 (동기·로딩 UI)
            ③ POST /{jobUuid}/request  S3 원본 확인 → QUEUED
            ④ (워커)                   PROCESSING → DONE | FAILED, 완료·실패 시 FCM
            ⑤ GET /{jobUuid}           FCM 을 받고 결과 화면 진입 시 1회 조회
            ```

            - **인증 필요.** 잡은 호출자 것이다.
            - **영상 바이트는 API 서버를 지나지 않는다.** 업로드는 클라이언트 → S3 직행이다.
            - **챌린지와 모티프가 같은 엔드포인트다** — `jobType` 이 가른다.
            - `UPLOADED` 는 "업로드가 끝났다"가 아니라 **"업로드 URL 을 내줬다"**는 뜻이다.
              실제 업로드 여부는 §13.2 가 S3 HEAD 로 확인한다.

            ### 두 형태 — `jobType` 이 정한다

            | jobType | 필수 | 보내지 않는다 | participantMask |
            |---|---|---|---|
            | `CHALLENGE` | `challengeSeq` | `assetSeq` | 선택 |
            | `MOTIF` | `assetSeq` | `challengeSeq` | **무시된다** |

            `challengeSeq`·`assetSeq` 가 어긋나면 400 `INVALID_PARAMETER` 이고 `data` 에 필드
            오류 배열이 실린다 — 무시하지 않고 거절한다. DB 도 두 칸이 함께 차는 것을 막는다.
            `participantMask` 만 MOTIF 에서 조용히 무시된다.

            ### participantMask — 슬롯 조합 (CHALLENGE)

            §7.2 의 `participants[].participantMask` 는 슬롯마다 **단일 비트**(1, 2, 4 …)다.
            여러 슬롯을 고르면 **비트 OR 로 합산**해서 보낸다 — 윈터(1) + 닝닝(2) = `3`.

            | 값 | 서버 동작 |
            |---|---|
            | `0` 또는 생략 | 무교체 — `edited_challenge_seq = NULL` 로 원본 위에 합성 |
            | `> 0` | `(challengeSeq, participantMask)` 로 편집본을 조회해 잡에 기록. **없으면 404** |

            `EDITED_CHALLENGE_NOT_FOUND` 는 **운영 전제가 깨졌을 때만** 나온다 — 어드민이 슬롯
            전 조합(`1 … 2ⁿ−1`)의 편집본을 등록하기로 했기 때문이다(§7.2). 사용자가 이 오류를
            보면 촬영 직전에 막힌 것이다.

            ### MOTIF 이용 권한 — 선결제 원칙

            권한 없이는 촬영을 시작할 수 없다. 판정은 §11 의 `myAccess` 와 **같은 규칙**이다.

            | 내 상태 | 결과 |
            |---|---|
            | ACTIVE 개별 구매 보유 (`OWNED`) | 201 |
            | 유효 패스 보유 (`PASS`) — `NORMAL`·`SECRET` | 201 |
            | 유효 패스 보유 — **`PREMIUM`** | 403 `ASSET_ACCESS_DENIED` (패스로 열리지 않는다) |
            | 없음 (`NONE`) | 403 `ASSET_ACCESS_DENIED` → 클라이언트는 **구매 화면으로 유도** |

            **판매 중단(`is_active=FALSE`) 에셋이어도 권한이 있으면 진행한다** — 어제 산 에셋이
            오늘 내려갔다고 내 촬영이 막히면 안 된다. §11.2 상세가 그 에셋을 404 로 숨기는 것과
            다른 판정이고, 의도된 차이다.

            ### uploadUrl

            **`video/mp4` · 크기 상한이 서명에 박혀 있다.** 다른 Content-Type 이나 상한을 넘는
            크기로 PUT 하면 S3 가 거부한다 — 서버가 바이트를 보지 못하므로 이것이 유일한 강제
            수단이다. 만료(`expiresIn` 초)가 지나면 §13.1 부터 다시 부른다.
            """;

    String REQUEST_CHALLENGE_EXAMPLE = """
            { "jobType": "CHALLENGE", "challengeSeq": 11, "participantMask": 3 }""";

    String REQUEST_MOTIF_EXAMPLE = """
            { "jobType": "MOTIF", "assetSeq": 21 }""";

    String CREATE_EXAMPLE = """
            {
              "result": "SUCCESS",
              "code": "SUCCESS",
              "message": "요청이 성공했습니다.",
              "data": {
                "jobUuid": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
                "uploadUrl": "https://motif-media.s3.ap-northeast-2.amazonaws.com/raw/3fa85f64.mp4?X-Amz-Algorithm=...&X-Amz-Signature=...",
                "expiresIn": 600
              },
              "timestamp": "2026-09-10T00:00:00.000Z"
            }""";

    String SHAPE_FAIL_EXAMPLE = """
            {
              "result": "FAIL",
              "code": "INVALID_PARAMETER",
              "message": "요청 값이 올바르지 않습니다.",
              "data": [
                { "field": "assetSeq", "message": "CHALLENGE 잡에는 보내지 않습니다." }
              ],
              "timestamp": "2026-09-10T00:00:00.000Z"
            }""";

    String NOT_FOUND_EXAMPLE = """
            {
              "result": "FAIL",
              "code": "EDITED_CHALLENGE_NOT_FOUND",
              "message": "존재하지 않는 편집본입니다.",
              "data": null,
              "timestamp": "2026-09-10T00:00:00.000Z"
            }""";

    String ACCESS_DENIED_EXAMPLE = """
            {
              "result": "FAIL",
              "code": "ASSET_ACCESS_DENIED",
              "message": "구매 후 이용할 수 있습니다.",
              "data": null,
              "timestamp": "2026-09-10T00:00:00.000Z"
            }""";

    @Operation(summary = "합성 잡 생성 — 촬영본 업로드 URL 발급 (§13.1)", description = CREATE_DESCRIPTION)
    @RequestBody(required = true, description = "jobType 에 따라 두 형태 중 하나",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = CompositeJobCreateRequest.class),
                    examples = {
                            @ExampleObject(name = "CHALLENGE — 슬롯 0·1 교체",
                                    value = REQUEST_CHALLENGE_EXAMPLE),
                            @ExampleObject(name = "MOTIF — 에셋 합성", value = REQUEST_MOTIF_EXAMPLE)
                    }))
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201", description = "잡 생성됨 — uploadUrl 로 원본을 PUT 한다",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = CompositeJobCreateResponse.class),
                            examples = @ExampleObject(value = CREATE_EXAMPLE))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "jobType 없음 · 형태 불일치 · participantMask 가 음수 (`INVALID_PARAMETER`)",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = FailResponseSchema.class),
                            examples = @ExampleObject(value = SHAPE_FAIL_EXAMPLE))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "MOTIF 이용 권한 없음 (`ASSET_ACCESS_DENIED`) — 구매 화면으로 유도",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = FailResponseSchema.class),
                            examples = @ExampleObject(value = ACCESS_DENIED_EXAMPLE))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "대상 없음 — `CHALLENGE_NOT_FOUND`(삭제 포함) · `EDITED_CHALLENGE_NOT_FOUND` · `ASSET_NOT_FOUND`",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = FailResponseSchema.class),
                            examples = @ExampleObject(value = NOT_FOUND_EXAMPLE)))
    })
    ApiResponse<CompositeJobCreateResponse> create(
            @Parameter(hidden = true) AuthenticatedUser user,
            CompositeJobCreateRequest request);

    // ---------------------------------------------------------------------------------
    // 13.2 합성 요청
    // ---------------------------------------------------------------------------------

    String REQUEST_DESCRIPTION = """
            파이프라인의 **3단계**다. 업로드를 마친 뒤 부르면 잡이 `UPLOADED → QUEUED` 로
            넘어간다. 이후 `PROCESSING → DONE | FAILED` 는 워커와 이벤트가 처리하고, 완료·실패
            시 **FCM** 이 나간다.

            - **인증 필요.** 본인 잡만 요청할 수 있다 — 타인 잡은 404.
            - **202 Accepted.** 접수했다는 뜻이며 합성은 아직 끝나지 않았다.
            - **폴링하지 않는다**(§1.6). FCM 을 받고 §13.3 을 **한 번** 부른다.

            ### 서버 처리 순서

            1. **S3 원본 존재 확인(HEAD)** — 업로드가 실제로 끝났는지 본다. 없으면 409
               `RAW_NOT_UPLOADED` (업로드 미완료 또는 URL 만료)
            2. 상태가 `UPLOADED` 가 아니면 409 `JOB_NOT_REQUESTABLE` — **중복 요청이 여기 걸린다**
            3. `UPLOADED → QUEUED` 전이 후 커밋

            영상 바이트가 서버를 지나지 않으므로 업로드 완료를 아는 방법은 HEAD 뿐이다.

            ### correctionEnabled

            | jobType | 본문 | 저장되는 값 |
            |---|---|---|
            | `CHALLENGE` | `{ "correctionEnabled": true }` | 보낸 값. **생략하면 `false`** *(기본안)* |
            | `MOTIF` | `{}` (또는 본문 없음) | 언제나 `false` — 보정을 쓰지 않는다. 값이 와도 무시 |

            `correctionEnabled` 는 촬영 후 사용자가 보정 여부를 고르는 값이라 §13.1 이 아니라
            여기서 정해진다.
            """;

    String REQUEST_CHALLENGE_BODY_EXAMPLE = """
            { "correctionEnabled": true }""";

    String REQUEST_MOTIF_BODY_EXAMPLE = "{}";

    String REQUEST_ACCEPTED_EXAMPLE = """
            {
              "result": "SUCCESS",
              "code": "SUCCESS",
              "message": "요청이 성공했습니다.",
              "data": { "jobUuid": "3fa85f64-5717-4562-b3fc-2c963f66afa6", "status": "QUEUED" },
              "timestamp": "2026-09-10T00:00:00.000Z"
            }""";

    String JOB_NOT_FOUND_EXAMPLE = """
            {
              "result": "FAIL",
              "code": "JOB_NOT_FOUND",
              "message": "존재하지 않는 합성 잡입니다.",
              "data": null,
              "timestamp": "2026-09-10T00:00:00.000Z"
            }""";

    String RAW_NOT_UPLOADED_EXAMPLE = """
            {
              "result": "FAIL",
              "code": "RAW_NOT_UPLOADED",
              "message": "원본 업로드가 완료되지 않았습니다.",
              "data": null,
              "timestamp": "2026-09-10T00:00:00.000Z"
            }""";

    @Operation(summary = "합성 요청 — 업로드 완료 후 큐 적재 (§13.2)", description = REQUEST_DESCRIPTION)
    @RequestBody(required = false, description = "CHALLENGE 만 값을 갖는다. MOTIF 는 빈 객체",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = CompositeJobRequestBody.class),
                    examples = {
                            @ExampleObject(name = "CHALLENGE — 보정 사용",
                                    value = REQUEST_CHALLENGE_BODY_EXAMPLE),
                            @ExampleObject(name = "MOTIF — 빈 객체",
                                    value = REQUEST_MOTIF_BODY_EXAMPLE)
                    }))
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "202", description = "접수됨 — 큐에 들어갔다",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = CompositeJobRequestResponse.class),
                            examples = @ExampleObject(value = REQUEST_ACCEPTED_EXAMPLE))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "잡 없음·**타인 잡** (`JOB_NOT_FOUND`)",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = FailResponseSchema.class),
                            examples = @ExampleObject(value = JOB_NOT_FOUND_EXAMPLE))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "`RAW_NOT_UPLOADED`(S3 원본 부재) · `JOB_NOT_REQUESTABLE`(상태가 UPLOADED 가 아님 — 중복 요청 포함)",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = FailResponseSchema.class),
                            examples = @ExampleObject(value = RAW_NOT_UPLOADED_EXAMPLE)))
    })
    ApiResponse<CompositeJobRequestResponse> request(
            @Parameter(hidden = true) AuthenticatedUser user,
            @Parameter(description = "잡 식별자", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            UUID jobUuid,
            CompositeJobRequestBody body);

    // ---------------------------------------------------------------------------------
    // 13.3 잡 상태·결과 조회
    // ---------------------------------------------------------------------------------

    String STATUS_DESCRIPTION = """
            FCM 을 받고 결과 화면에 진입할 때 **1회** 호출한다. **폴링 금지**(§1.6) — 상태
            확인은 푸시를 받고 이 API 를 한 번 부르는 것뿐이다.

            - **인증 필요.** 본인 잡만 — 타인 잡은 404 `JOB_NOT_FOUND`(존재 은닉).
            - **잡이 실패해도 조회는 200 이다.** `status` 로 판단하고 `failCode` 로 안내한 뒤
              "다시 시도"(§13.4)를 노출한다.
            - `QUEUED`·`PROCESSING` 이면 **분기 필드가 전부 `null`** 이다. 앱은 둘을 같은
              "진행 중" 으로 그린다.

            ### 응답은 jobType 으로 갈린다

            | jobType | 분기 필드 |
            |---|---|
            | `CHALLENGE` | `resultVideoUrl` · `resultThumbnailUrl` |
            | `MOTIF` | `resultSeq` — **URL 이 없다** |

            **MOTIF 에 URL 을 싣지 않는 것은 누락이 아니다**(R8 확정). 재생과 문구 입력은
            §11.6·§11.8 이 담당하므로 `resultSeq` 로 보관함 상세로 이동한다. 반대로 CHALLENGE
            응답에는 `resultSeq` 칸이 없다.

            ### CHALLENGE 결과는 보호 등급이다

            게시 전 프리뷰라 서명이 붙는다(§1.7). `resultVideoUrl` 은 core-api 가 서명해 다시
            쓴 **HLS 마스터 재생목록**이고, 조각 요청에 같은 파라미터를 붙이는 것은 앱의
            인터셉터 몫이다(ADR-0012). 이 화면에서 확인한 뒤 **§8.8 로 게시**한다.

            ### MOTIF 의 resultSeq 는 DONE 이면 항상 있다

            결과 행 생성에 실패하면 잡을 `DONE` 으로 만들지 않고 FCM 도 보내지 않기 때문이다.

            | 필드 | 설명 |
            |---|---|
            | `status` | `UPLOADED` \\| `QUEUED` \\| `PROCESSING` \\| `DONE` \\| `FAILED` |
            | `attempt` | 시도 횟수. 재시도(§13.4)할 때마다 증가한다 |
            | `failCode` | 실패 사유. `FAILED` 가 아니면 `null` |
            """;

    String STATUS_CHALLENGE_DONE_EXAMPLE = """
            {
              "result": "SUCCESS",
              "code": "SUCCESS",
              "message": "요청이 성공했습니다.",
              "data": {
                "jobUuid": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
                "jobType": "CHALLENGE",
                "status": "DONE",
                "attempt": 1,
                "failCode": null,
                "resultVideoUrl": "https://cdn.motif.io/results/challenge/3fa85f64-5717-4562-b3fc-2c963f66afa6/master.m3u8",
                "resultThumbnailUrl": "https://cdn.motif.io/results/challenge/3fa85f64-5717-4562-b3fc-2c963f66afa6/thumb.jpg"
              },
              "timestamp": "2026-09-10T00:00:00.000Z"
            }""";

    String STATUS_MOTIF_DONE_EXAMPLE = """
            {
              "result": "SUCCESS",
              "code": "SUCCESS",
              "message": "요청이 성공했습니다.",
              "data": {
                "jobUuid": "9c1d2e3f-4a5b-6c7d-8e9f-0a1b2c3d4e5f",
                "jobType": "MOTIF",
                "status": "DONE",
                "attempt": 1,
                "failCode": null,
                "resultSeq": 10
              },
              "timestamp": "2026-09-10T00:00:00.000Z"
            }""";

    String STATUS_PROCESSING_EXAMPLE = """
            {
              "result": "SUCCESS",
              "code": "SUCCESS",
              "message": "요청이 성공했습니다.",
              "data": {
                "jobUuid": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
                "jobType": "CHALLENGE",
                "status": "PROCESSING",
                "attempt": 1,
                "failCode": null,
                "resultVideoUrl": null,
                "resultThumbnailUrl": null
              },
              "timestamp": "2026-09-10T00:00:00.000Z"
            }""";

    String STATUS_FAILED_EXAMPLE = """
            {
              "result": "SUCCESS",
              "code": "SUCCESS",
              "message": "요청이 성공했습니다.",
              "data": {
                "jobUuid": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
                "jobType": "CHALLENGE",
                "status": "FAILED",
                "attempt": 2,
                "failCode": "FACE_NOT_DETECTED",
                "resultVideoUrl": null,
                "resultThumbnailUrl": null
              },
              "timestamp": "2026-09-10T00:00:00.000Z"
            }""";

    @Operation(summary = "잡 상태·결과 조회 — FCM 수신 후 1회 (§13.3)", description = STATUS_DESCRIPTION)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "실패한 잡도 200 이다 — `status` 로 판단한다",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = CompositeJobStatusResponse.class),
                            examples = {
                                    @ExampleObject(name = "CHALLENGE — DONE",
                                            value = STATUS_CHALLENGE_DONE_EXAMPLE),
                                    @ExampleObject(name = "MOTIF — DONE (resultSeq)",
                                            value = STATUS_MOTIF_DONE_EXAMPLE),
                                    @ExampleObject(name = "진행 중 — 분기 필드가 전부 null",
                                            value = STATUS_PROCESSING_EXAMPLE),
                                    @ExampleObject(name = "실패 — 200 + failCode",
                                            value = STATUS_FAILED_EXAMPLE)
                            })),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "잡 없음·**타인 잡** (`JOB_NOT_FOUND`)",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = FailResponseSchema.class),
                            examples = @ExampleObject(value = JOB_NOT_FOUND_EXAMPLE)))
    })
    ApiResponse<CompositeJobStatusResponse> status(
            @Parameter(hidden = true) AuthenticatedUser user,
            @Parameter(description = "잡 식별자", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            UUID jobUuid);

    // ---------------------------------------------------------------------------------
    // 13.4 재시도
    // ---------------------------------------------------------------------------------

    String RETRY_DESCRIPTION = """
            실패한 잡을 다시 돌린다. 대상은 **본인의 `FAILED` 잡**이고 요청 본문이 없다 —
            무엇을 다시 할지는 잡 행에 다 적혀 있다.

            - **인증 필요.** 타인 잡은 404.
            - **같은 잡 행을 재큐한다** — 새 잡을 만들지 않으므로 **`jobUuid` 가 유지된다.**
              들고 있던 식별자로 §13.3 을 계속 조회하면 된다.
            - **202 Accepted.** 접수했다는 뜻이며 합성은 아직 시작도 하지 않았다.

            ### 서버 처리

            1. `status: FAILED → QUEUED`
            2. `fail_code = NULL` 초기화
            3. `attempt + 1`
            4. 디스패처가 다음 틱에 같은 조립기로 재접수한다

            결과 프리픽스가 `results/{challenge|motif}/{jobUuid}/` 로 **멱등**하게 정해져
            있어, 재합성은 같은 경로를 덮어쓴다.

            ### 시도 상한이 없다 *(기본안)*

            사용자가 직접 누르는 재시도는 막지 않는다. 디스패처의 `attempt < 3` 은 **접수
            실패 자동 재큐**의 무한 루프를 막는 장치라 별개다.

            ### 직전 실패 사유는 지워진다

            재큐하면 `fail_code` 가 `NULL` 이 된다(상태별 CHECK 유지). 실패 이력이 필요하면
            로그·이벤트로 남긴다 — 행에 보존하지 않는다.

            ### 410 은 재촬영 신호다

            `RAW_VIDEO_EXPIRED` 는 촬영 원본이 **수명주기로 삭제됐다**는 뜻이다. "있었지만
            이제 없다" 라 409 가 아니라 410 이고, 클라이언트는 **재시도 버튼을 숨기고
            재촬영을 안내**해야 한다.
            """;

    String RETRY_ACCEPTED_EXAMPLE = """
            {
              "result": "SUCCESS",
              "code": "SUCCESS",
              "message": "요청이 성공했습니다.",
              "data": { "jobUuid": "3fa85f64-5717-4562-b3fc-2c963f66afa6", "status": "QUEUED", "attempt": 2 },
              "timestamp": "2026-09-10T00:00:00.000Z"
            }""";

    String NOT_RETRYABLE_EXAMPLE = """
            {
              "result": "FAIL",
              "code": "JOB_NOT_RETRYABLE",
              "message": "재시도할 수 없는 상태입니다.",
              "data": null,
              "timestamp": "2026-09-10T00:00:00.000Z"
            }""";

    String RAW_EXPIRED_EXAMPLE = """
            {
              "result": "FAIL",
              "code": "RAW_VIDEO_EXPIRED",
              "message": "원본 영상이 만료되었습니다. 다시 촬영해 주세요.",
              "data": null,
              "timestamp": "2026-09-10T00:00:00.000Z"
            }""";

    @Operation(summary = "재시도 — 실패한 잡을 같은 행으로 재큐 (§13.4)", description = RETRY_DESCRIPTION)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "202", description = "재큐됨 — jobUuid 는 그대로다",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = CompositeJobRetryResponse.class),
                            examples = @ExampleObject(value = RETRY_ACCEPTED_EXAMPLE))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "잡 없음·**타인 잡** (`JOB_NOT_FOUND`)",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = FailResponseSchema.class),
                            examples = @ExampleObject(value = JOB_NOT_FOUND_EXAMPLE))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409", description = "`FAILED` 상태가 아님 (`JOB_NOT_RETRYABLE`)",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = FailResponseSchema.class),
                            examples = @ExampleObject(value = NOT_RETRYABLE_EXAMPLE))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "410",
                    description = "원본이 수명주기로 삭제됨 (`RAW_VIDEO_EXPIRED`) — 재촬영을 안내한다",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = FailResponseSchema.class),
                            examples = @ExampleObject(value = RAW_EXPIRED_EXAMPLE)))
    })
    ApiResponse<CompositeJobRetryResponse> retry(
            @Parameter(hidden = true) AuthenticatedUser user,
            @Parameter(description = "잡 식별자", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            UUID jobUuid);
}
