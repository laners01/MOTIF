package com.ssafy.motif.coreapi.admin.challenge.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 등록 전 사전 검증 결과 (어드민 명세 §3.7.8).
 *
 * <p><b>실패해도 200 이다.</b> 이건 "등록해 봐"가 아니라 "등록할 수 있나"를 묻는
 * dry-run 이라, 편집본이 덜 찬 상태가 오류가 아니라 <b>정상적인 중간 상태</b>다.
 * 화면은 슬롯 표를 그리는 동안 이 API 를 계속 부르며 빈 칸을 표시한다.
 *
 * @param slotMasks 슬롯 수에서 나오는 마스크 {@code 1, 2, 4, 8} — 슬롯 i 는 {@code 1 << i} 다.
 *                  0019 이전의 {@code computedMasks}(조합 {@code 1 … 2ⁿ−1})를 대신한다
 * @param issues    통과면 빈 배열. 등록 API 가 던졌을 실패를 그대로 옮긴 것이라
 *                  {@code code}·{@code data} 가 등록 실패 응답과 같다
 */
@Schema(description = "등록 전 사전 검증(dry-run) 결과")
public record ChallengeValidateResponse(
        boolean valid,
        List<Integer> slotMasks,
        List<Issue> issues) {

    /** @param data 등록 실패 응답의 {@code data} 와 같다 — 슬롯 위반 목록·부재 키 목록 등 */
    public record Issue(String code, String message, Object data) {
    }
}
