package com.ssafy.motif.domain.core.video;

import java.time.Instant;
import java.util.UUID;

/**
 * 어드민 참여 영상 조회 투영 (어드민 명세 §3.12.1·§3.12.2).
 *
 * <p>{@link VideoFeedRow} 와 칸이 같고 <b>{@code deleted}·{@code reportCount} 둘만
 * 더 있다.</b> 명세가 항목을 "API §8.2 + visibility·isDeleted·reportCount" 로 못박았다.
 *
 * <p><b>{@code VideoFeedRow} 에 칸을 더하지 않는 이유.</b> 그 투영은 팬 API 네 곳
 * (§6.1·§8.1·§8.2·§4.8)이 쓴다. 어드민만 필요한 칸을 거기 넣으면 그 쿼리들이 전부
 * 신고 수를 세게 되는데, 피드는 한 페이지에 30건이라 그 상관 서브쿼리가 그대로 곱해진다.
 *
 * <p>{@link #feedRow()} 로 되돌려 {@code VideoItemAssembler} 를 그대로 쓴다 — URL
 * 조립(보호 등급 판정·재생목록 주소)을 어드민이 다시 구현하지 않기 위해서다.
 */
public record AdminVideoRow(
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
        UUID jobUuid,
        boolean deleted,
        Long reportCount) {

    public VideoFeedRow feedRow() {
        return new VideoFeedRow(videoSeq, challengeSeq, userSeq, description,
                videoKey, thumbnailKey, durationMs, viewCount, likeCount, commentCount,
                hasArtistLike, hasArtistComment, commentEnabled, visibility, regDttm,
                authorNickname, authorProfileImageKey, jobUuid);
    }
}
