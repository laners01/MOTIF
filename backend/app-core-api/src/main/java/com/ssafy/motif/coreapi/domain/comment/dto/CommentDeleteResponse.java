package com.ssafy.motif.coreapi.domain.comment.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 삭제 결과 (API 명세 §9.4).
 *
 * @param deletedCount <b>실제로 삭제된 행 수</b>. 루트를 지우면 직계 답글도 함께
 *                     소프트 삭제되므로 1보다 클 수 있다(루트 1 + 답글 2 = 3).
 *                     이미 삭제돼 있던 답글은 세지 않는다 — 이 값이 그대로 대상의
 *                     {@code comment_count} 감소폭이라, 부풀면 카운터가 어긋난다
 */
@Schema(description = "삭제 결과 (§9.4)")
public record CommentDeleteResponse(int deletedCount) {
}
