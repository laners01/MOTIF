package com.ssafy.motif.domain.core.challenge;

/**
 * 아티스트 랭킹 점수의 재료가 되는 챌린지 한 줄 (API 명세 §5.1) —
 * {@link ChallengeRepository#findRankingInputs} 투영.
 *
 * <p><b>{@code regDttm} 이 빠지고 {@code challengeSeq} 가 들어왔다</b>
 * *(2026-09-21 — ADR-0016)*. 나이는 감쇠에 쓰던 값인데 감쇠를 걷어냈고, 대신 최근·이전
 * 구간 조회수를 <b>Redis 버킷에서 챌린지별로</b> 찾아야 해서 키가 필요해졌다.
 *
 * <p>표시용 칸은 랭킹 계산에 쓰이지 않으므로 {@code Challenge} 엔티티를 통째로 끌어오지
 * 않는다 — 주기마다 전량을 읽는 경로다.
 *
 * @param participantCount 참여 영상 수. <b>누적 항에만 들어간다</b> — 1시간 창에서는
 *                         0~2건이라 환산비가 아니라 on/off 스위치가 된다(ADR-0016)
 */
public record ChallengeScoreRow(
        Integer challengeSeq, Integer artistSeq, long viewCount, int participantCount) {
}
