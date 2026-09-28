package com.ssafy.motif.coreapi.domain.comment.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * 댓글 항목 (API 명세 §9.1) — 챌린지·참여 영상·공지 <b>공통</b>.
 *
 * <p>명세 §9 가 세 대상을 하나의 계약으로 못박았다. 대상마다 항목 타입을 두면 안드로이드가
 * 같은 댓글 UI 를 세 벌 들고 있어야 한다.
 *
 * @param replyCount 접힌 답글 수 — 펼치기는 §9.2 다. <b>노출되는</b> 답글만 센다(삭제된
 *                   답글·삭제 사용자의 답글 제외, §1.8). {@code comments.comment_count}
 *                   칼럼이 아니다 — 그쪽은 루트+답글 합계이고 비노출을 모른다
 * @param isMine     {@code true} 면 클라이언트가 삭제 메뉴를 노출한다(§9.4)
 * @param regDttm    <b>원시각</b>이다 — "n시간 전" 표기는 클라이언트가 만든다
 */
@Schema(description = "댓글 항목 (§9.1)")
public record CommentItem(
        Long commentSeq,
        String content,
        Author author,
        Integer likeCount,

        @Schema(description = "요청자가 좋아요를 눌렀는가")
        boolean isLiked,

        @Schema(description = "접힌 답글 수 — §9.2 로 펼친다")
        long replyCount,

        @Schema(description = "요청자 본인의 댓글인가 — 삭제 메뉴 노출 기준")
        boolean isMine,

        Instant regDttm
) {

    /**
     * 작성자 요약.
     *
     * @param isArtist <b>이 콘텐츠의 아티스트</b>인가 — 화면의 아티스트 뱃지이자 목록
     *                 정렬 1순위와 <b>같은 판정</b>이다. {@code role=ARTIST} 가 아니다:
     *                 다른 아티스트 계정이 남의 챌린지에 단 댓글은 일반 댓글이다(§9.1)
     */
    public record Author(Integer userSeq, String nickname, String profileImageUrl,
                         boolean isArtist) {
    }
}
