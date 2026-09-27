package com.ssafy.motif.domain.core.motif;

import java.time.Instant;

/**
 * 어드민 모티프 결과물 조회 투영 (어드민 명세 §3.17.1).
 *
 * <p>아티스트 이름은 싣지 않는다 — 아티스트 이름은 {@code users.nickname} 이라 조인이
 * 하나 더 필요한데, 화면은 필터 드롭다운용으로 아티스트 목록을 이미 받아 둔다.
 */
public record AdminMotifResultRow(
        Long resultSeq,
        Integer userSeq,
        String userNickname,
        Integer artistSeq,
        Integer assetSeq,
        String assetTitle,
        String description,
        String videoKey,
        String thumbnailKey,
        Integer durationMs,
        boolean deleted,
        Instant regDttm) {
}
