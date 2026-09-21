package com.ssafy.motif.coreapi.admin.challenge.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 슬롯 편집본 키 교체 결과 (어드민 명세 §3.7.6). */
@Schema(description = "슬롯 편집본")
public record ParticipantEditedResponse(
        Integer challengeParticipantSeq,
        Integer artistSeq,
        int slotOrder,
        int participantMask,
        String editedChallengeUrl,
        String initialSceneUrl,
        @Schema(description = "선택 화면 이미지. 아직 안 올렸으면 null", nullable = true)
        String previewImageUrl) {
}
