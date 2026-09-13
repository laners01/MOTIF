package com.ssafy.motif.coreapi.admin.notice.dto;

/**
 * 공지 작성 응답 (어드민 명세 §3.10.2 — 201 {@code data}).
 *
 * <p>seq 하나만 준다. 화면(572)은 등록 후 목록이나 수정 화면으로 이동하며 거기서
 * 상세(§3.10.3)를 다시 읽는다 — 등록 응답에 전체를 실을 이유가 없다.
 */
public record NoticeCreateResponse(Integer noticeSeq) {
}
