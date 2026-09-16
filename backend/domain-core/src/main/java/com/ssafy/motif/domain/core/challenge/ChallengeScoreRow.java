package com.ssafy.motif.domain.core.challenge;

import java.time.Instant;

/**
 * 랭킹 점수의 재료가 되는 챌린지 한 줄 (API 명세 §5.1) —
 * {@link ChallengeRepository#findRankingInputs} 투영.
 *
 * <p>칸이 넷뿐인 이유: 점수는 <b>반응 두 개와 나이</b>에서만 나온다. 제목·썸네일 같은
 * 표시용 칸은 랭킹 계산에 쓰이지 않으므로 {@code Challenge} 엔티티를 통째로 끌어오지
 * 않는다 — 5분마다 전량을 읽는 경로다.
 *
 * @param regDttm 감쇠의 기준. {@code viewCount}·{@code participantCount} 가 누적
 *                카운터라 개별 조회·참여의 발생 시각이 DB 에 없어, 걸 수 있는 시간축이
 *                챌린지 등록 시각 하나뿐이다
 */
public record ChallengeScoreRow(
        Integer artistSeq, long viewCount, int participantCount, Instant regDttm) {
}
