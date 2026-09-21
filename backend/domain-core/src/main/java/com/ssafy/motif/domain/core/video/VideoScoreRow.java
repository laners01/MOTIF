package com.ssafy.motif.domain.core.video;

/**
 * HOT 랭킹 점수의 재료가 되는 참여 영상 한 줄 (API 명세 §6.1) —
 * {@link ParticipationVideoRepository#findRankingInputs} 투영.
 *
 * <p>칸이 둘뿐인 이유: 점수의 나머지 재료(최근 1시간·이전 1시간 조회수)는 <b>DB 가 아니라
 * Redis 버킷</b>에서 온다(ADR-0015). 썸네일·곡·작성자 같은 표시용 칸은 카드를 그릴 때
 * 필요하고 랭킹 계산에는 쓰이지 않으므로 {@code ParticipationVideo} 엔티티를 통째로
 * 끌어오지 않는다 — 주기마다 전량을 읽는 경로다.
 *
 * <p><b>{@code regDttm} 이 빠졌다</b> *(2026-09-21 — ADR-0015)*. 점수에 시간 감쇠가 있던
 * 시절의 재료다. 감쇠의 기준이 게시 시각이었던 것은 {@code view_count} 가 누적 카운터라
 * 개별 조회의 발생 시각이 DB 에 없었기 때문인데, 버킷이 그 시간축을 직접 주면서 감쇠와
 * 함께 필요가 없어졌다. <b>되살리면 오래된 영상이 지금 떠도 올라오지 못한다.</b>
 *
 * <p><b>{@code videoKey} 를 싣지 않는다.</b> 한때 "공개 사본이 준비된 영상만" 을 가리려고
 * 배치가 {@code MediaKeys.isProtected} 로 봤으나, 공개 사본 트리가 폐기되면서 그 구간
 * 자체가 사라졌다(ADR-0013). 참여 영상은 전부 보호 등급이라 프리픽스로 거를 것이 없다 —
 * 되살리면 모든 후보가 떨어져 HOT 이 영구히 빈다.
 */
public record VideoScoreRow(Long videoSeq, Long viewCount) {
}
