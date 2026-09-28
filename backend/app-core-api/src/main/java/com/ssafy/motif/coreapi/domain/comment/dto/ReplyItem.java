package com.ssafy.motif.coreapi.domain.comment.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * 답글 항목 (API 명세 §9.2) — <b>{@link CommentItem} 에서 {@code replyCount} 가 빠진
 * 모양</b>이다.
 *
 * <p><b>{@code CommentItem} 을 재사용하고 {@code replyCount} 를 0 이나 {@code null} 로
 * 채우지 않는다.</b> 답글에는 답글이 없다 — 깊이가 2단으로 못박혀 있어서(§9) 그 칸은
 * "0개" 가 아니라 <b>개념이 없다</b>. 0 을 내보내면 클라이언트가 펼치기 버튼을 그릴지
 * 말지를 값으로 판단하게 되고, 언젠가 §9.1 이 그 칸을 손대면 답글 쪽이 조용히 따라
 * 바뀐다.
 *
 * <p>{@code author} 는 §9.1 과 같은 {@link CommentItem.Author} 다 — 작성자 요약은
 * 정말로 같은 것이라 형태가 갈리면 안 된다({@code isArtist} 판정까지 같다).
 */
@Schema(description = "답글 항목 (§9.2)")
public record ReplyItem(
        Long commentSeq,
        String content,
        CommentItem.Author author,
        Integer likeCount,

        @Schema(description = "요청자가 좋아요를 눌렀는가")
        boolean isLiked,

        @Schema(description = "요청자 본인의 답글인가 — 삭제 메뉴 노출 기준")
        boolean isMine,

        Instant regDttm
) {
}
