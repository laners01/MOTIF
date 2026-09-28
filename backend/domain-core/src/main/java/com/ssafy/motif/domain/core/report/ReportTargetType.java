package com.ssafy.motif.domain.core.report;

/**
 * 신고 대상 종류 (어드민 명세 §3.13.1) — {@code reports} 의 4개 대상 컬럼과 1:1 이다.
 *
 * <p>DB 는 어느 칸이 채워졌는지로 종류를 표현한다(4칸 중 정확히 하나). 그 판정을
 * 화면·서비스가 각자 하면 갈리므로 {@link Report#targetType()} 한 곳에서만 한다.
 *
 * <p><b>댓글 3종은 아직 조회·처리할 수 없다.</b> 댓글 엔티티가 없어(§9 미구현) 대상
 * 미리보기를 조립할 수 없고, 인용 시 답글 연쇄·좋아요 하드 삭제 같은 공통 삭제 정책도
 * 구현할 수 없다. 다만 <b>그 행이 생길 수도 없다</b> — 댓글 신고 API(§9.6) 자체가
 * 없어서 지금 {@code reports} 에 들어올 수 있는 것은 VIDEO 뿐이다.
 */
public enum ReportTargetType {
    VIDEO,
    CHALLENGE_COMMENT,
    VIDEO_COMMENT,
    NOTICE_COMMENT
}
