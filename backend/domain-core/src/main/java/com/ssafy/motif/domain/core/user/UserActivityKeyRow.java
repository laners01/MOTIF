package com.ssafy.motif.domain.core.user;

import java.time.Instant;

/**
 * 내 활동 카드 한 자리의 <b>식별 정보만</b> (API 명세 §4.9) —
 * {@link UserActivityRepository#findActivityKeys} 투영.
 *
 * <p>인터페이스 투영인 것은 네이티브 쿼리라서다(§6.3 {@code ActivityKeyRow} 와 같은
 * 이유). 생성자 투영은 JPQL 에서만 쓸 수 있다.
 *
 * @see UserActivityRepository#findActivityKeys 왜 카드 본문을 함께 읽지 않는지
 */
public interface UserActivityKeyRow {

    Long getVideoSeq();

    /**
     * 정렬 기준 — 이 영상에 대한 <b>내</b> 마지막 상호작용 시각(좋아요·댓글 중 나중).
     *
     * <p>응답의 {@code lastInteractedDttm} 이 이 값이다. 영상의 등록 시각이 아니다.
     */
    Instant getLastInteractedDttm();
}
