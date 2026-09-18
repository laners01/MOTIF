package com.ssafy.motif.coreapi.admin.challenge.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 편집본 키 교체 (어드민 명세 §3.7.6) — 부분 수정이라 보낸 칸만 바뀐다.
 *
 * <p><b>조합({@code participantMask})은 바뀌지 않는다.</b> 같은 행의 키만 갱신하므로
 * 전조합 완결성이 깨질 수 없고, 그래서 합성 잡이 참조 중이어도 허용된다 —
 * 결과물은 별도 오브젝트라 이미 만들어진 결과가 바뀌지 않고 다음 합성부터 새 키를 쓴다.
 *
 * <p>{@code previewImageKey} 는 0017 이전 행(값이 NULL)을 채우는 통로이기도 하다.
 *
 * @param durationMs 선택 — 보내면 원본 길이와 ±허용 오차를 대조한다(§1.4)
 */
@Schema(description = "편집본 키 교체")
public record EditedChallengeUpdateRequest(
        String videoKey,
        String initialSceneKey,
        String previewImageKey,
        Long durationMs) {
}
