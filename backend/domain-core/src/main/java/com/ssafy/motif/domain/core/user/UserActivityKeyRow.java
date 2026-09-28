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

    /**
     * 대상의 종류 — {@link ActivityTargetType} 의 이름이다.
     *
     * <p>enum 이 아니라 {@code String} 인 것은 네이티브 쿼리의 투영이기 때문이다.
     * {@code union all} 의 각 갈래가 리터럴로 적어 내보내고, 값으로 바꾸는 것은
     * 호출부가 한다 — 여기서 enum 을 선언하면 Hibernate 의 문자열→enum 변환에
     * 기대게 되는데 그 변환은 투영 종류에 따라 동작이 갈린다.
     */
    String getTargetType();

    /**
     * 대상의 식별자 — {@code VIDEO} 면 {@code video_seq}, {@code CHALLENGE} 면
     * {@code challenge_seq} 다.
     *
     * <p><b>{@code getTargetType()} 없이 이 값만 쓰면 안 된다.</b> 두 seq 공간은
     * 겹친다 — 같은 12번이 양쪽에 다 있을 수 있다.
     */
    Long getTargetSeq();

    /**
     * 정렬 기준 — 이 대상에 대한 <b>내</b> 마지막 상호작용 시각(좋아요·댓글 중 나중).
     *
     * <p>응답의 {@code lastInteractedDttm} 이 이 값이다. 대상의 등록 시각이 아니다.
     */
    Instant getLastInteractedDttm();
}
