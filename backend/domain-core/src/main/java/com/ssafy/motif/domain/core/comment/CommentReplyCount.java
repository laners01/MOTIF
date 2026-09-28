package com.ssafy.motif.domain.core.comment;

/**
 * 루트 댓글 한 건의 답글 수 (API 명세 §9.1 {@code replyCount}) — 페이지 전체를 한 번에.
 *
 * <p><b>0건인 루트 댓글은 결과에 행이 없다.</b> {@code group by} 의 성질이라 그렇고,
 * 호출부가 {@code getOrDefault(seq, 0)} 으로 받는다.
 *
 * @param commentSeq 부모(루트) 댓글의 PK
 * @param count      <b>노출되는</b> 답글 수 — 삭제된 답글과 삭제 사용자가 쓴 답글은
 *                   목록에서 빠지므로(§1.8) 세지 않는다. {@code comment_count} 칼럼을
 *                   쓰지 않는 이유이기도 하다(그쪽은 루트+답글 합계이고 비노출을 모른다)
 */
public record CommentReplyCount(Long commentSeq, long count) {
}
