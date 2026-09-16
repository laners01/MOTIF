package com.ssafy.motif.coreapi.admin.video.dto;

import com.ssafy.motif.coreapi.domain.video.dto.VideoItem;
import io.swagger.v3.oas.annotations.media.Schema;
import com.ssafy.motif.domain.core.video.Visibility;

import java.time.Instant;

/**
 * 어드민 참여 영상 항목 (어드민 명세 §3.12.1·§3.12.2).
 *
 * <p>명세가 "API §8.2 + {@code visibility}·{@code isDeleted}·{@code reportCount}" 로
 * 못박았다. {@code visibility} 는 §8.2 항목에 이미 있으므로 실제로 더해지는 것은 둘이다.
 *
 * <p><b>{@code VideoItem} 을 감싸지 않고 펼친다.</b> 감싸면 응답이
 * {@code {video:{…}, isDeleted}} 가 돼 §8.2 와 모양이 달라진다 — 어드민 화면과 앱이
 * 같은 항목을 다르게 파싱하게 된다. 값 조립은 {@code VideoItemAssembler} 가 그대로 하고
 * 여기서는 펼치기만 한다.
 */
@Schema(description = "어드민 참여 영상 항목 (§3.12.1·§3.12.2)")
public record AdminVideoItem(
        Long videoSeq,
        Integer challengeSeq,
        String description,
        String videoUrl,
        String thumbnailUrl,
        Integer durationMs,
        VideoItem.Author author,
        Long viewCount,
        Integer likeCount,
        Integer commentCount,
        boolean hasArtistLike,
        boolean hasArtistComment,
        boolean commentEnabled,
        Visibility visibility,
        Instant regDttm,

        @Schema(description = "소프트 삭제 여부. 어드민은 삭제분도 본다 — 신고 대상이 이미 지워졌는지 판단해야 한다")
        boolean isDeleted,

        @Schema(description = "이 영상에 누적된 신고 수. 여러 건 쌓인 것을 먼저 보라는 신호다")
        long reportCount) {

    /** {@code liked}·{@code isMine} 은 어드민 관점에 의미가 없어 조립에서 뺀다. */
    public static AdminVideoItem of(VideoItem video, boolean deleted, long reportCount) {
        return new AdminVideoItem(
                video.videoSeq(), video.challengeSeq(), video.description(),
                video.videoUrl(), video.thumbnailUrl(), video.durationMs(), video.author(),
                video.viewCount(), video.likeCount(), video.commentCount(),
                video.hasArtistLike(), video.hasArtistComment(), video.commentEnabled(),
                video.visibility(), video.regDttm(), deleted, reportCount);
    }
}
