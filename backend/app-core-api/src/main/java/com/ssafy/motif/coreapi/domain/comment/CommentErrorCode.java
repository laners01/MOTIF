package com.ssafy.motif.coreapi.domain.comment;

import com.ssafy.motif.common.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 댓글 도메인 에러 코드 (API 명세 §15 표).
 *
 * <p><b>{@code *_NOT_FOUND} 는 여기 없다.</b> 댓글이 붙는 대상이 없을 때의 404 는
 * 대상 도메인의 코드를 그대로 쓴다({@code CHALLENGE_NOT_FOUND}·{@code VIDEO_NOT_FOUND}·
 * {@code NOTICE_NOT_FOUND}) — 명세 §9 머리말이 "대상 종류에 따라" 라고 못박았고,
 * 같은 뜻의 코드를 도메인마다 새로 만들면 안드로이드의 분기가 갈린다.
 * 댓글 자체의 {@code COMMENT_NOT_FOUND}(§9.2 답글·§9.4 삭제·§9.5 좋아요·§9.6 신고)는
 * 그것을 처음 쓰는 티켓이 여기 더한다.
 */
@Getter
@RequiredArgsConstructor
public enum CommentErrorCode implements ErrorCode {

    /**
     * 참여 영상의 {@code comment_enabled=FALSE} (API 명세 §9.1·§9.2·§9.3·§9.5).
     *
     * <p><b>목록 조회까지 막는다.</b> 댓글 사용 중지는 데이터 동결이라 기존 댓글을 지우지
     * 않고 접근만 막는 장치다 — 읽기를 열어 두면 동결의 의미가 없다.
     *
     * <p>빈 목록(200)으로 대신하지 않는다. 클라이언트가 "아직 댓글이 없습니다" 와
     * "댓글을 쓸 수 없습니다" 를 다르게 그려야 한다.
     */
    COMMENT_DISABLED(403, "COMMENT_DISABLED", "댓글을 사용할 수 없는 게시물입니다.");

    private final int status;
    private final String code;
    private final String message;
}
