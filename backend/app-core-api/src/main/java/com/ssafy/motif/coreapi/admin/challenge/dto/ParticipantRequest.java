package com.ssafy.motif.coreapi.admin.challenge.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * 교체 가능한 인물 슬롯 1개 + 그 사람을 지운 편집본 (어드민 명세 §3.7.2).
 *
 * <p><b>배열 순서가 화면 좌→우다.</b> 서버가 그 순서대로 {@code slot_order = index} 를
 * 부여한다 — 요청에 순서 칸이 없는 것이 의도다(수기 입력 → 오류 차단).
 *
 * <p><b>편집본이 여기 실린다</b>(0019). 예전에는 {@code editedChallenges[]} 라는 별도 배열에
 * {@code removedSlots} 조합을 적었지만, 편집본이 슬롯 1개당 1개가 되면서 그 사람을 지운
 * 영상이 그 사람의 슬롯에 붙는 것이 자연스러워졌다.
 *
 * @param editedChallengeKey 이 아티스트를 지운 편집본 영상(AI팀 산출). {@code initialSceneKey}
 *                           와 <b>짝</b>이라 함께 보내거나 함께 비워야 한다. 둘 다 비면 편집본이
 *                           없는 슬롯이고, 사용자가 그 슬롯을 고르면 §13.1 이 404 로 막는다
 * @param previewImageKey    촬영 전 선택 화면에서 이 슬롯을 골랐을 때 썸네일 자리에 보이는
 *                           이미지. 편집본이 있어도 생략할 수 있다 — 클라이언트가 챌린지
 *                           썸네일로 폴백한다
 * @param durationMs         편집본 길이(ms). <b>편집본을 보내면 필수다</b>(0020) — 원본
 *                           ±허용 오차 대조(§1.4)에만 쓰고 버리던 값인데, 앱 촬영 타이머가
 *                           이 값에 맞춰 돌게 되면서 저장한다. 업로드 화면 JS 가
 *                           {@code <video>} 에서 읽어 자동으로 싣는다
 */
@Schema(description = "인물 슬롯 + 편집본 (배열 순서 = 화면 좌→우)")
public record ParticipantRequest(
        @NotNull Integer artistSeq,
        @Valid @NotNull AnchorRequest anchor,
        String editedChallengeKey,
        String initialSceneKey,
        String previewImageKey,
        Long durationMs) {

    /** 편집본을 함께 보냈는가 — 키 짝은 {@code SlotRules.checkParticipants} 가 이미 봤다. */
    public boolean hasEdited() {
        return editedChallengeKey != null && !editedChallengeKey.isBlank();
    }
}
