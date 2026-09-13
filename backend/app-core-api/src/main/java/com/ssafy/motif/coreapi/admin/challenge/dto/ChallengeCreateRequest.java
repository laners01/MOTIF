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
 * @param durationMs    업로드 화면 JS 가 {@code <video>} 메타데이터에서 읽어 실은 값
 *                      (§1.4). core-api 에는 ffmpeg 가 없다 — 서버는 대조만 한다
 * @param thumbnailAtMs 썸네일로 쓸 시각(ms). <b>선택 — 없으면 워커 기본값 1500</b>.
 *                      등록 화면에서 원본을 스크럽해 고른다. 이미지를 따로 올리는
 *                      방식이 아니라 ffmpeg 가 원본에서 그 프레임을 뽑으므로, 새 S3 키
 *                      규약도 presign 종류 추가도 없고 화질이 원본 그대로 나온다.
 *                      폼 값이라 임의의 수가 올 수 있어 {@code durationMs} 와 대조한다
 */
@Schema(description = "챌린지 등록 (원본 + 슬롯 + 편집본 원자)")
public record ChallengeCreateRequest(
        @NotNull Integer artistSeq,
        @NotNull Integer songSeq,
        @NotBlank String description,
        @NotBlank String videoKey,
        @NotNull @Positive Long durationMs,
        Long thumbnailAtMs,
        @NotNull @Positive Integer width,
        @NotNull @Positive Integer height,
        @Valid @NotNull List<ParticipantRequest> participants,
        @Valid @NotNull List<EditedChallengeRequest> editedChallenges) {

    public ChallengeSlotsRequest slots() {
        return new ChallengeSlotsRequest(participants, editedChallenges);
    }
}
