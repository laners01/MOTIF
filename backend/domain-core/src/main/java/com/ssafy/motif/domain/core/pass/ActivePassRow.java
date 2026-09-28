package com.ssafy.motif.domain.core.pass;

import java.time.Instant;

/**
 * 보유 중인 모티프 패스 한 줄 (어드민 명세 §3.14.2 {@code activePasses}) —
 * {@link MotifPassRepository#findActive} 투영.
 *
 * <p>아티스트 이름은 {@code users.nickname} 이다 — {@code artists} 에 이름 칸이 없다
 * ({@code Artist} javadoc).
 */
public record ActivePassRow(Integer artistSeq, String artistName, Instant expiresDttm) {
}
