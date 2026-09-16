package com.ssafy.motif.domain.core.video;

import java.time.Instant;

/**
 * HOT 랭킹 점수의 재료가 되는 참여 영상 한 줄 (API 명세 §6.1) —
 * {@link ParticipationVideoRepository#findRankingInputs} 투영.
 *
 * <p>칸이 넷뿐인 이유: 점수는 <b>조회수와 나이</b>에서만 나온다(§6.1 랭킹 기본안 —
 * 좋아요는 쓰지 않는다). 썸네일·곡·작성자 같은 표시용 칸은 카드를 그릴 때 필요하고
 * 랭킹 계산에는 쓰이지 않으므로 {@code ParticipationVideo} 엔티티를 통째로 끌어오지
 * 않는다 — 5분마다 전량을 읽는 경로다.
 *
 * @param regDttm  감쇠의 기준 = <b>게시 시각</b>. {@code view_count} 가 누적 카운터라
 *                 개별 조회의 발생 시각이 DB 에 없어, 걸 수 있는 시간축이 이것뿐이다
 *                 ({@code ChallengeScoreRow} 와 같은 사정)
 * @param videoKey 보호 등급 제외 판정용. <b>점수에는 쓰지 않는다</b> — 공개 사본이
 *                 준비되지 않은 영상을 스냅샷에서 빼기 위해 배치가
 *                 {@code MediaKeys.isProtected} 로 본다. 판정을 SQL 프리픽스 리터럴로
 *                 옮기면 등급 규약이 두 곳으로 갈라진다
 */
public record VideoScoreRow(
        Long videoSeq, Long viewCount, Instant regDttm, String videoKey) {
}
