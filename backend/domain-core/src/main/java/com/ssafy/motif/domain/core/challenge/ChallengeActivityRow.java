package com.ssafy.motif.domain.core.challenge;

/**
 * 내 활동 카드의 챌린지 쪽 본문 (API 명세 §4.9) —
 * {@link ChallengeRepository#findActivityCards} 투영.
 *
 * <p><b>{@code ChallengeListRow} 와 합치지 않는다.</b> 그쪽은 어드민 목록(§3.7.1)이라
 * 아티스트·곡·슬롯 수를 조인해 오는 무거운 행이고, 여기는 카드 한 장을 그릴 세 칸이
 * 전부다. 합치면 활동 목록이 쓰지 않는 조인을 페이지마다 끌고 다닌다.
 *
 * <p><b>참여 영상 쪽 {@code VideoFeedRow} 와도 합치지 않는다.</b> 같은 카드를 그리지만
 * 출처가 다르다 — 참여 영상은 재생에 필요한 {@code jobUuid} 를 {@code composite_jobs}
 * 조인으로 얻어야 하는데, 챌린지는 잡이 없고 카운터를 자기 행에 직접 들고 있다.
 *
 * <p>썸네일은 <b>키</b>다 — URL 로 바꾸는 것은 등급 판정이 있는 core-api 쪽이다.
 *
 * <p>전부 래퍼 타입인 이유는 {@link ChallengeListRow} 와 같다 — JPQL 생성자 표현식이
 * 박싱된 값을 넘긴다.
 */
public record ChallengeActivityRow(
        Integer challengeSeq,
        String thumbnailKey,
        Long viewCount,
        Integer likeCount
) {
}
