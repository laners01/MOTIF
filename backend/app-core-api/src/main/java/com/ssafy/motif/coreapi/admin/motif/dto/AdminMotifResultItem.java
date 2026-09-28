package com.ssafy.motif.coreapi.admin.motif.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/** 어드민 모티프 결과물 항목 (어드민 명세 §3.17.1). */
@Schema(description = "어드민 모티프 결과물 항목 (§3.17.1)")
public record AdminMotifResultItem(
        Long resultSeq,
        Integer userSeq,
        String userNickname,
        Integer artistSeq,
        Integer assetSeq,
        String assetTitle,
        String description,

        @Schema(description = "master.m3u8 + 프리픽스 서명. 조각 요청에도 같은 쿼리를 붙여야 한다(ADR-0012)")
        String videoUrl,

        String thumbnailUrl,
        Integer durationMs,
        boolean isDeleted,
        Instant regDttm) {
}
