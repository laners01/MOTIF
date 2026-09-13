package com.ssafy.motif.coreapi.domain.challenge.docs;

import com.ssafy.motif.common.response.ApiResponse;
import com.ssafy.motif.coreapi.domain.challenge.dto.ChallengeDetailResponse;
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
 * 팬 앱 챌린지 조회 API 문서 계약 (명세 §7.2).
 *
 * <p>Swagger 애너테이션만 여기 둔다 — 매핑은 구현 클래스가 갖는다
 * (팀 표준, {@code backend/README.md}).
 */
@Tag(name = "챌린지 원본 (CHALLENGE)", description = "명세 §7 — 아티스트 원본 챌린지")
public interface ChallengeControllerDocs {

    @Operation(
            summary = "챌린지 상세 (§7.2)",
            description = """
                    챌린지 상세 Bottom Sheet 의 데이터다 — §7.1 피드 항목 + `description` + 슬롯 정보 (명세 §7.2).

                    - **인증 필요.** `isLiked`·`isSubscribed` 가 호출자 기준이다.
                    - `videoUrl` 은 **단일 MP4 가 아니라 마스터 재생목록(`master.m3u8`)** 이다 (§1.7, ADR-0010 D2).
                      플레이어가 그 재생목록을 읽고 화질별 재생목록·조각을 이어서 요청한다.
                      챌린지 원본은 **공개 등급**이라 서명 없는 CloudFront URL 이며, 피드에서 참여 영상과
                      같이 스와이프되므로 참여 영상과 마찬가지로 HLS 다.
                    - `thumbnailUrl` 은 첫 프레임 로딩 전 플레이스홀더다 (회의 확정 R2).
                    - **`title` 이 없다** — R3 에서 `challenges.title` 컬럼이 삭제됐다. 화면 식별은
                      곡명(`song.title`)과 아티스트로 한다.
                    - `participantCount` 는 게시 참여 수이며 **PUBLIC·PRIVATE 를 모두 포함**한다.
                      PRIVATE 영상의 조회 비노출(§1.8)과 카운트 집계는 분리된 규칙이다.
                    - `participants` 는 교체 가능한 인물 슬롯이다. `participantMask` 는 **단일 비트**
                      (1·2·4·8)이며 배열은 마스크 오름차순 = 화면 좌→우 순서다.
                      `bbox` 는 **원본 영상 px · 좌상단 원점** 좌표로 촬영 가이드 오버레이용이다.
                      `profileImageUrl` 은 설명 시트의 참여자 아바타용이며 미등록이면 `null` 이다.
                    - **편집본이 0개인 챌린지가 있다.** 슬롯은 등록됐지만 AI 편집본이 아직 없는
                      상태로, 재생·표시는 정상이고 촬영만 §13.1 의 `EDITED_CHALLENGE_NOT_FOUND` 로
                      막힌다. 어드민 등록이 **0개 또는 정확히 2ⁿ−1개**만 허용하므로 부분 집합은
                      존재하지 않는다 — 조합 하나만 비는 경우는 없다.
                    - **`availableMasks` 는 없다** (회의 확정 R4). 조합 유효성은 §13.1 의
                      `EDITED_CHALLENGE_NOT_FOUND` 로만 사후 판별된다.
                    - 삭제된 챌린지는 **없는 것으로 답한다**(404) — §1.8 공통 비노출.

                    댓글은 §9 공통 댓글(`type=challenge`)을 쓴다.
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "챌린지 상세",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "result": "SUCCESS",
                                      "code": "SUCCESS",
                                      "message": "요청이 성공했습니다.",
                                      "data": {
                                        "challengeSeq": 11,
                                        "videoUrl": "https://cdn.../challenges/{uuid}/master.m3u8",
                                        "thumbnailUrl": "https://cdn.../challenges/{uuid}/thumb.jpg",
                                        "durationMs": 15000,
                                        "artist": { "artistSeq": 3, "name": "카리나", "profileImageUrl": "https://cdn.../profiles/3.jpg" },
                                        "song": { "title": "Supernova", "singerName": "aespa" },
                                        "viewCount": 120000,
                                        "participantCount": 340,
                                        "likeCount": 5600,
                                        "commentCount": 210,
                                        "isLiked": false,
                                        "isSubscribed": true,
                                        "description": "같이 춰요!",
                                        "participants": [
                                          {
                                            "artistSeq": 7,
                                            "name": "윈터",
                                            "participantMask": 1,
                                            "profileImageUrl": "https://cdn.../profiles/7.jpg",
                                            "bbox": { "leftTopX": 120, "leftTopY": 80, "rightBottomX": 460, "rightBottomY": 940 }
                                          }
                                        ]
                                      },
                                      "timestamp": "2026-09-14T00:00:00.000Z"
                                    }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "미존재·삭제 챌린지",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = FailResponseSchema.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "result": "FAIL",
                                      "code": "CHALLENGE_NOT_FOUND",
                                      "message": "존재하지 않는 챌린지입니다.",
                                      "data": null,
                                      "timestamp": "2026-09-14T00:00:00.000Z"
                                    }""")))
    })
    ApiResponse<ChallengeDetailResponse> detail(
            @Parameter(hidden = true) AuthenticatedUser user,
            @Parameter(description = "챌린지 seq", example = "11") Integer challengeSeq);
}
