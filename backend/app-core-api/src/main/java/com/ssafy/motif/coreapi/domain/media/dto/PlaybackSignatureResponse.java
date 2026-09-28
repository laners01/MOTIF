package com.ssafy.motif.coreapi.domain.media.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * 보호 등급 재생 서명 재발급 결과 (ADR-0012).
 *
 * <p>클라이언트(OkHttp 인터셉터)가 조각·자식 재생목록 요청에 <b>그대로 붙일</b> 값이다.
 * 파라미터를 하나씩 쪼개 주지 않는 이유는 조립 규칙이 클라이언트로 넘어가기 때문이다 —
 * 붙이는 순서·인코딩이 어긋나면 서명이 깨지는데 그 증상이 전부 403 이라 원인을 찾기 어렵다.
 */
@Schema(description = "재생 서명 재발급 (ADR-0012)")
public record PlaybackSignatureResponse(

        @Schema(description = "요청 URL 에 붙일 쿼리스트링 — 물음표는 포함하지 않는다",
                example = "Policy=eyJTdGF0ZW1lbnQiOlt7...&Signature=Xyz~&Key-Pair-Id=K27QE85H6DLYDD")
        String query,

        @Schema(description = "이 파라미터가 통하는 시각까지. 지나면 조각 요청이 403 이다")
        Instant expiresDttm) {
}
