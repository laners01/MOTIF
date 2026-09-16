package com.ssafy.motif.domain.core.video;

import java.time.Instant;
import java.util.UUID;

/**
 * 참여 영상 피드 카드 한 건 (API 명세 §8.1) —
 * {@link ParticipationVideoRepository#findFeedCards} 투영.
 *
 * <p><b>§6.1 {@code HotVideoRow} 와 합치지 않는다.</b> 필드가 겹쳐 보이지만 담는 것이
 * 다르다 — HOT 카드는 곡 정보를 싣고 좋아요·댓글 수를 싣지 않는 경량 항목이고(회의
 * 확정 R2), 여기는 뷰어 오버레이 전량을 싣는 대신 곡을 싣지 않는다. 지금 합치면 양쪽
 * 모두 쓰지 않는 칸을 절반씩 들고 다니게 된다.
 *
 * @param jobUuid  보호 등급 영상의 재생목록 주소를 만드는 재료 (§1.7). 공개 사본이
 *                 준비된 영상에는 쓰이지 않지만 <b>행마다 조인해 온다</b> — 보호 등급인
 *                 것만 골라 다시 읽으면 페이지당 왕복이 하나 더 생긴다
 * @param regDttm  게시 시각. 정렬에는 쓰지 않는다(§8.1 은 추천 정렬이다)
 */
public record VideoFeedRow(
        Long videoSeq,
        Integer challengeSeq,
        Integer userSeq,
        String description,
        String videoKey,
        String thumbnailKey,
        Integer durationMs,
        Long viewCount,
        Integer likeCount,
        Integer commentCount,
        boolean hasArtistLike,
        boolean hasArtistComment,
        boolean commentEnabled,
        Visibility visibility,
        Instant regDttm,
        String authorNickname,
        String authorProfileImageKey,
        UUID jobUuid
) {
}
