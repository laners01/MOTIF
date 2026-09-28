package com.ssafy.motif.domain.core.artist;

/**
 * 아티스트별 집계 한 줄 — 목록의 {@code challengeCount}·{@code assetCount}·
 * {@code noticeCount} (어드민 명세 §3.5.1).
 *
 * <p>행마다 {@code count(*)} 를 날리면 한 페이지(최대 100)에 300 왕복이 된다. 세 리포지토리가
 * 각자 <b>페이지의 seq 전부를 한 번에</b> 집계해 이 모양으로 돌려주고, 서비스가 Map 으로
 * 붙인다 — 왕복은 페이지당 3회로 고정된다.
 */
public record ArtistCount(Integer artistSeq, long count) {
}
