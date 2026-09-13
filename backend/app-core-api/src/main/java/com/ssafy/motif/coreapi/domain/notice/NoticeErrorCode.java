package com.ssafy.motif.coreapi.domain.notice;

import com.ssafy.motif.common.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 공지 도메인 에러 코드 (API 명세 §15 · 어드민 명세 §5).
 *
 * <p>사용자 API(§10 공지 조회·좋아요·댓글)와 어드민(§3.10)이 함께 쓰는 코드라
 * {@code AdminErrorCode} 가 아니라 여기가 소유한다 — {@code ArtistErrorCode} 와 같은
 * 근거(code 전역 유일).
 */
@Getter
@RequiredArgsConstructor
public enum NoticeErrorCode implements ErrorCode {

    /** 미존재 또는 소프트 삭제된 공지 — 삭제분은 없는 것으로 답한다. */
    NOTICE_NOT_FOUND(404, "NOTICE_NOT_FOUND", "존재하지 않는 공지입니다.");

    private final int status;
    private final String code;
    private final String message;
}
