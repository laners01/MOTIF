package com.ssafy.motif.coreapi.admin.song.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * 곡 목록 항목 (어드민 명세 §3.6.1).
 *
 * @param challengeCount 이 곡을 쓰는 <b>삭제되지 않은</b> 챌린지 수 — "지금 쓰이고 있나"를
 *                       보는 값이다. 곡은 삭제할 수 없으므로 참조 차단용이 아니라 참고용이다
 */
@Schema(description = "곡")
public record SongResponse(
        Integer songSeq,
        String title,
        String singerName,
        long challengeCount,
        Instant regDttm) {
}
