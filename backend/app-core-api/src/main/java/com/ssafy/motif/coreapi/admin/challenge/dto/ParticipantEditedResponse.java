package com.ssafy.motif.coreapi.admin.challenge.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 슬롯 편집본 키 교체 결과 (어드민 명세 §3.7.6).
 *
 * <p><b>{@code participantMask} 가 없다</b>(0020) — 슬롯을 가리키는 값은
 * {@code challengeParticipantSeq} 하나로 통일됐다. 마스크는 AI 접수 페이로드 안에만
 * 남고 서버가 {@code 1 << slotOrder} 로 파생한다.
 */
@Schema(description = "슬롯 편집본")
public record ParticipantEditedResponse(
        Integer challengeParticipantSeq,
        Integer artistSeq,
        int slotOrder,
        String editedChallengeUrl,
        String initialSceneUrl,
        @Schema(description = "선택 화면 이미지. 아직 안 올렸으면 null", nullable = true)
        String previewImageUrl,
        @Schema(description = "편집본 길이(ms). 앱 §7.6 이 촬영 타이머에 쓴다", example = "15020")
        Integer durationMs) {
}
