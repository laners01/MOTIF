package com.ssafy.motif.coreapi.admin.challenge.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

/**
 * 챌린지 등록 요청 (어드민 명세 §3.7.2) — 사전 검증(§3.7.8)도 같은 본문을 쓴다.
 *
 * <p><b>원본·슬롯·편집본을 한 요청으로 받는다.</b> 전부 유효할 때만 생성하므로
 * "편집본이 덜 올라간 미공개 챌린지" 상태가 필요 없다 — {@code challenges} 에 상태
 * 컬럼이 없는 근거다(§ADMIN-06).
 *
 * @param durationMs 업로드 화면 JS 가 {@code <video>} 메타데이터에서 읽어 실은 값
 *                   (§1.4). core-api 에는 ffmpeg 가 없다 — 서버는 대조만 한다
 */
@Schema(description = "챌린지 등록 (원본 + 슬롯 + 편집본 원자)")
public record ChallengeCreateRequest(
        @NotNull Integer artistSeq,
        @NotNull Integer songSeq,
        @NotBlank String description,
        @NotBlank String videoKey,
        @NotNull @Positive Long durationMs,
        @NotNull @Positive Integer width,
        @NotNull @Positive Integer height,
        @Valid @NotNull List<ParticipantRequest> participants,
        @Valid @NotNull List<EditedChallengeRequest> editedChallenges) {

    public ChallengeSlotsRequest slots() {
        return new ChallengeSlotsRequest(participants, editedChallenges);
    }
}
