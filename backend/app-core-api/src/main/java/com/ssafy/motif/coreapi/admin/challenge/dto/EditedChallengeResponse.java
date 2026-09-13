package com.ssafy.motif.coreapi.admin.challenge.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 편집본 키 교체 결과 (어드민 명세 §3.7.6). */
@Schema(description = "편집본")
public record EditedChallengeResponse(
        Integer editedChallengeSeq,
        int participantMask,
        String videoUrl,
        String initialSceneUrl) {
}
