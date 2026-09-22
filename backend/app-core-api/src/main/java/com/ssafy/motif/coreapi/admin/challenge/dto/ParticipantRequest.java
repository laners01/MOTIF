package com.ssafy.motif.coreapi.admin.challenge.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * 교체 가능한 인물 슬롯 1개 (어드민 명세 §3.7.2).
 *
 * <p><b>배열 순서가 화면 좌→우다.</b> 서버가 그 순서대로 {@code slot_order = index} 를
 * 부여한다 — 요청에 순서 칸이 없는 것이 의도다(수기 입력 → 오류 차단).
 *
 * <p><b>편집본은 여기 없다</b>(2026-09-22). 편집본 키는 슬롯에 매여 있는데
 * ({@code private/edited/{challengeSeq}/{challengeParticipantSeq}/…}) 등록 요청이
 * 만들어지는 시점에는 <b>두 seq 가 아직 없다</b> — 이 요청이 INSERT 되면서 비로소
 * 생긴다. 그래서 슬롯을 먼저 만들고 §3.7.6 으로 편집본을 채운다. 입구를 둘로 두면
 * 한쪽으로 들어온 키가 경로 규약을 지키지 않는다.
 *
 * <p>편집본이 없는 슬롯은 정상이고, 사용자가 그 슬롯을 고르면 §13.1 이 404 로 막는다.
 */
@Schema(description = "인물 슬롯 (배열 순서 = 화면 좌→우). 편집본은 §3.7.6 으로 따로 올린다")
public record ParticipantRequest(
        @NotNull Integer artistSeq,
        @Valid @NotNull AnchorRequest anchor) {
}
