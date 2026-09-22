package com.ssafy.motif.domain.core.user;

/**
 * 내 활동 카드가 가리키는 <b>대상의 종류</b> (API 명세 §4.9).
 *
 * <p><b>왜 필요한가.</b> 활동 카드는 참여 영상과 챌린지 원본 둘 다에서 나오는데
 * {@code participation_videos.video_seq}(BIGINT)와 {@code challenges.challenge_seq}
 * (INTEGER)는 <b>서로 다른 seq 공간</b>이다. 숫자만으로는 88번이 어느 쪽인지 알 수
 * 없어, 앱이 어느 상세 화면으로 갈지 정하지 못하고 커서의 타이브레이크도 성립하지
 * 않는다(두 대상이 같은 밀리초에 같은 seq 를 가질 수 있다).
 *
 * <p><b>이름이 계약의 일부다.</b> 응답 {@code target.type} 과 커서 토큰에 이 상수의
 * 이름이 그대로 실리고, 정렬 타이브레이크도 이 이름의 문자열 비교로 갈린다
 * ({@code UserActivityRepository.ACTIVITY_ORDER}). 바꾸면 이미 발급된 커서가
 * 어긋나므로 값을 늘리는 것은 되지만 이름을 고치는 것은 안 된다.
 */
public enum ActivityTargetType {

    /** 참여 영상 — {@code video_likes} · {@code video_comments}. */
    VIDEO,

    /** 챌린지 원본 — {@code challenge_likes} · {@code challenge_comments}. */
    CHALLENGE
}
