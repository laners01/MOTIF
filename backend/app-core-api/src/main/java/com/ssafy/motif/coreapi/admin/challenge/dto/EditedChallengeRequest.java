package com.ssafy.motif.coreapi.admin.challenge.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 슬롯 조합 하나에 대응하는 편집본 (어드민 명세 §3.7.2).
 *
 * @param removedSlots    {@code participants} 배열의 <b>인덱스</b> 집합 — 서버가
 *                        {@code mask = Σ 2^idx} 로 환산한다. 비어 있으면 마스크가 0 이 되는데
 *                        0 은 "무교체(원본)" 예약값이라 요청할 수 없다
 * @param previewImageKey 촬영 전 선택 화면에서 이 조합을 골랐을 때 썸네일 자리에 보이는
 *                        이미지(AI팀 산출). mask 당 1장이라 편집본 행에 붙인다
 * @param durationMs      선택 — 보내면 원본 ±허용 오차를 대조한다(§1.4). 업로드 화면 JS 가
 *                        읽어 싣는 값이며, 없으면 길이 검증을 건너뛴다
 */
@Schema(description = "조합별 편집본")
public record EditedChallengeRequest(
        @NotNull List<Integer> removedSlots,
        @NotBlank String videoKey,
        @NotBlank String initialSceneKey,
        @NotBlank String previewImageKey,
        Long durationMs) {
}
