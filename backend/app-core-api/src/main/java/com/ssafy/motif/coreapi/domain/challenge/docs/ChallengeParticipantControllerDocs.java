package com.ssafy.motif.coreapi.domain.challenge.docs;

import com.ssafy.motif.common.response.ApiResponse;
import com.ssafy.motif.coreapi.domain.challenge.dto.EditedVideoResponse;
import com.ssafy.motif.coreapi.global.security.AuthenticatedUser;
import com.ssafy.motif.coreapi.global.docs.FailResponseSchema;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

/** §7.6 Swagger 문서 — 구현은 {@code ChallengeParticipantController}. */
@Tag(name = "Challenge", description = "챌린지 (명세 §7)")
public interface ChallengeParticipantControllerDocs {

    @Operation(
            summary = "편집본 영상 (§7.6)",
            description = """
                    촬영 화면이 배경으로 재생할 **편집본 영상**이다 — 그 슬롯의 아티스트가 지워진 영상.

                    - **인증 필요.**
                    - **HLS 가 아니라 통짜 MP4 다.** 편집본은 후처리(`job.media`)를 타지 않아 렌디션이
                      없다. 15초 내외 한 편을 촬영 시작 전에 받아 두는 쓰임이라 재생 중 화질 전환이
                      필요 없고, 같은 파일을 AI 워커도 합성 베이스로 받는다(그쪽은 MP4 만 읽는다).
                      ABR 이 필요한 것은 피드처럼 연속 재생되는 영상이다(ADR-0010 D2).
                    - **서명이 붙는다.** 키가 `private/edited/…` 라 CloudFront 가 뷰어 접근을 막고,
                      서버가 URL 하나를 서명해 내려준다. `expiresIn` 이 지나면 **이 API 를 다시 부른다** —
                      만료를 짧게 잡을 수 있는 이유가 그 복구 경로다.
                    - `durationMs` 를 서버가 주는 이유도 MP4 라서다. HLS 면 재생목록의 `#EXTINF` 합으로
                      플레이어가 알아내지만, MP4 는 `moov` 를 읽기 전까지 모른다 — 촬영 타이머를
                      재생 전에 그리려면 필요하다.
                    - 슬롯 식별자는 §7.5 가 준 `challengeParticipantSeq` 를 그대로 쓴다.

                    ### 404 는 세 경우를 한 코드로 묶는다

                    슬롯 없음 · 삭제된 챌린지 소속 · 편집본 미등록. 가르면 남의 챌린지 슬롯 seq 를
                    넣어 보고 존재를 읽을 수 있다(§1.8). 편집본 미등록은 **정상 상태**이므로
                    §7.5 의 `initialSceneUrl` 이 `null` 인 슬롯은 화면에서 미리 잠근다.
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "편집본 영상",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "result": "SUCCESS",
                                      "code": "SUCCESS",
                                      "message": "요청이 성공했습니다.",
                                      "data": {
                                        "challengeParticipantSeq": 31,
                                        "challengeSeq": 11,
                                        "artistSeq": 7,
                                        "videoUrl": "https://cdn.../private/edited/11/31/e1.mp4?Expires=...&Signature=...&Key-Pair-Id=...",
                                        "durationMs": 15020,
                                        "expiresIn": 600
                                      },
                                      "timestamp": "2026-09-21T00:00:00.000Z"
                                    }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "슬롯 없음 · 삭제된 챌린지 · 편집본 미등록",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = FailResponseSchema.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "result": "FAIL",
                                      "code": "EDITED_CHALLENGE_NOT_FOUND",
                                      "message": "존재하지 않는 편집본입니다.",
                                      "data": null,
                                      "timestamp": "2026-09-21T00:00:00.000Z"
                                    }""")))
    })
    ApiResponse<EditedVideoResponse> editedVideo(
            @Parameter(hidden = true) AuthenticatedUser user,
            @Parameter(description = "슬롯 seq (§7.5 응답)", example = "31") Integer challengeParticipantSeq);
}
