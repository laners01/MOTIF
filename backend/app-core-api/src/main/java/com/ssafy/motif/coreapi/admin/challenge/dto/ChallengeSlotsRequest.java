package com.ssafy.motif.coreapi.admin.challenge.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 슬롯 구성 전체 교체 요청 (어드민 명세 §3.7.5).
 *
 * <p>등록(§3.7.2)의 슬롯 부분과 <b>같은 모양</b>이다 — 원본·곡·메인 아티스트는 기존 행에서
 * 가져오므로 다시 받지 않는다. 검증도 등록과 같은 코드를 탄다.
 */
@Schema(description = "슬롯 구성 전체 교체")
public record ChallengeSlotsRequest(
        @Valid @NotNull List<ParticipantRequest> participants,
        @Valid @NotNull List<EditedChallengeRequest> editedChallenges) {
}
