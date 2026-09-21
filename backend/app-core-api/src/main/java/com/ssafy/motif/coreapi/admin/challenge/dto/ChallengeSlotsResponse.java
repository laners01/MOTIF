package com.ssafy.motif.coreapi.admin.challenge.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 등록·슬롯 교체 응답 (어드민 명세 §3.7.2 · §3.7.5 — "응답은 3.7.2 와 동일").
 *
 * <p>서버가 정한 순서와 그로부터 파생한 마스크를 그대로 돌려준다. 화면이 슬롯 표를 다시
 * 그릴 때 쓰고, 어드민이 순서·비트값을 입력하지 않았다는 사실을 응답으로 확인시켜 준다.
 */
@Schema(description = "등록·슬롯 교체 결과")
public record ChallengeSlotsResponse(
        Integer challengeSeq,
        int durationMs,
        String thumbnailUrl,
        List<Participant> participants) {

    /**
     * @param slotOrder       화면 좌→우, 0부터
     * @param participantMask {@code 1 << slotOrder} — 앱이 §13.1 에 그대로 실어 보낸다
     * @param hasEdited       이 슬롯을 지운 편집본이 올라와 있는가. {@code false} 면 사용자가
     *                        그 슬롯을 고를 때 §13.1 이 404 다
     */
    public record Participant(Integer challengeParticipantSeq, Integer artistSeq,
                              int slotOrder, int participantMask, boolean hasEdited) {
    }
}
