package com.ssafy.motif.coreapi.admin.challenge.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 등록·슬롯 교체 응답 (어드민 명세 §3.7.2 · §3.7.5 — "응답은 3.7.2 와 동일").
 *
 * <p>서버가 계산한 마스크를 그대로 돌려준다. 화면이 조합 표를 다시 그릴 때 쓰고,
 * 어드민이 비트값을 입력하지 않았다는 사실을 응답으로 확인시켜 준다.
 */
@Schema(description = "등록·슬롯 교체 결과")
public record ChallengeSlotsResponse(
        Integer challengeSeq,
        int durationMs,
        String thumbnailUrl,
        List<Participant> participants,
        List<Edited> editedChallenges) {

    public record Participant(Integer artistSeq, int participantMask) {
    }

    public record Edited(Integer editedChallengeSeq, int participantMask) {
    }
}
