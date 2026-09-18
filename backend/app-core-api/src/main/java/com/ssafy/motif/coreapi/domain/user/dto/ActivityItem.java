package com.ssafy.motif.coreapi.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/**
 * 내 활동 카드 한 장 (API 명세 §4.9) — <b>영상 하나가 카드 하나</b>다.
 *
 * <p>좋아요와 댓글을 탭으로 가르지 않는다(R2 재설계). 같은 영상에 둘 다 했으면 카드
 * 하나에 하트와 댓글이 함께 붙는다.
 *
 * <p><b>§9 의 {@code CommentItem}·{@code ReplyItem} 을 재사용하지 않는다.</b> 칸이
 * 겹쳐 보이지만 계약이 다르다 — 그쪽은 댓글 목록 화면이라 {@code isLiked}·{@code isMine}·
 * {@code author.isArtist} 로 좋아요 버튼·삭제 메뉴·아티스트 뱃지를 그린다. 여기는 활동
 * 이력이라 <b>전부 내 것</b>이고(그래서 {@code isMine} 이 뜻을 잃는다) 카드에서 답글에
 * 좋아요를 누르는 동작도 없다. 지금 합치면 §9 가 칸을 하나 더할 때마다 이 화면이 쓰지
 * 않는 값을 채워야 한다.
 *
 * @param liked              하트 표시 — 내가 이 영상에 좋아요를 눌렀는가
 * @param myComment          내가 단 댓글. <b>좋아요만 한 영상이면 {@code null}</b>
 * @param replies            내 댓글에 달린 답글 프리뷰. 없거나 내 댓글이 답글이면 {@code []}
 * @param lastInteractedDttm 정렬 기준 — 좋아요·댓글 중 <b>마지막</b> 상호작용 시각
 */
@Schema(description = "내 활동 카드 (§4.9)")
public record ActivityItem(
        Video video,

        @Schema(description = "내가 좋아요를 눌렀는가 — 카드의 하트 표시")
        boolean liked,

        MyComment myComment,
        List<Reply> replies,

        @Schema(description = "좋아요·댓글 중 마지막 상호작용 시각 (정렬 기준)")
        Instant lastInteractedDttm
) {

    /**
     * 카드가 가리키는 영상.
     *
     * <p>§4.1·§4.8 의 {@code VideoPreviewItem} 과 칸이 다르다 — 그쪽은 <b>내 영상</b>
     * 그리드라 {@code visibility} 를 싣고, 여기는 남의 공개 영상이라 그 칸이 항상
     * {@code PUBLIC} 이 된다. 대신 화면의 "♥ 1.2만" 을 그릴 {@code likeCount} 가 있다.
     */
    public record Video(Long videoSeq, String thumbnailUrl, Long viewCount, Integer likeCount) {
    }

    /**
     * 내가 단 댓글.
     *
     * @param replyCount     <b>노출되는</b> 답글 수(삭제된 답글·삭제 사용자의 답글 제외).
     *                       프리뷰보다 많으면 클라이언트가 게시물 댓글 화면으로 보낸다
     * @param parentComment  내 댓글이 <b>답글</b>이면 그 부모 요약, 루트면 {@code null}.
     *                       부모가 삭제됐거나 작성자가 탈퇴했어도 {@code null} 이다
     */
    public record MyComment(Long commentSeq, String content, Integer likeCount, long replyCount,
                            Instant regDttm, ParentComment parentComment) {
    }

    /** 부모 댓글 요약 — 답글이 어디에 달렸는지 보여 주는 한 줄. */
    public record ParentComment(Long commentSeq, Author author, String content) {
    }

    /** 내 댓글에 달린 답글 — <b>등록순</b>으로 싣는다(고르는 것은 최신순). */
    public record Reply(Long commentSeq, Author author, String content, Instant regDttm) {
    }

    /**
     * 작성자 요약.
     *
     * <p>{@code isArtist} 가 없다 — 아티스트 뱃지는 댓글 <b>목록</b>(§9.1)의 정렬 1순위와
     * 짝인 표시이고, 활동 카드에는 그 정렬도 뱃지도 없다.
     */
    public record Author(Integer userSeq, String nickname, String profileImageUrl) {
    }
}
