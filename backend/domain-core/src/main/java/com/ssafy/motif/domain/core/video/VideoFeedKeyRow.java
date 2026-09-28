package com.ssafy.motif.domain.core.video;

/**
 * 참여 영상 피드 한 자리의 <b>정렬 정보만</b> (API 명세 §8.1) —
 * {@link ParticipationVideoRepository#findFeedKeys} 투영.
 *
 * <p>인터페이스 투영인 것은 네이티브 쿼리라서다({@code ActivityKeyRow} 와 같은 방식).
 * 생성자 투영은 JPQL 에서만 쓸 수 있다.
 *
 * <p><b>{@code score} 를 굳이 실어 오는 이유는 커서다.</b> 다음 페이지 토큰이
 * {@code (점수, videoSeq)} 라 이번 페이지 마지막 항목의 점수가 필요한데, 그 값을
 * 카드 본문에서 다시 계산하면 공식이 SQL 과 Java 두 곳에 생긴다 — 한쪽만 고쳤을 때
 * <b>예외도 로그도 없이</b> 페이지 경계에서만 항목이 어긋난다.
 */
public interface VideoFeedKeyRow {

    Long getVideoSeq();

    /** {@link ParticipationVideoRepository#FEED_SCORE} 의 값. 순위 비교용일 뿐 응답에 나가지 않는다. */
    Long getScore();
}
