package com.ssafy.motif.coreapi.domain.comment.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 작성 결과 (API 명세 §9.3) — <b>식별자만</b> 준다.
 *
 * <p>작성된 댓글 전체를 돌려주지 않는다. 화면은 이미 본문·작성자를 알고 있어 로컬
 * 상태로 즉시 그리고, 정렬(§9.1 은 세 그룹 우선순위다)까지 맞춰야 할 때만 목록을 다시
 * 받는다.
 */
@Schema(description = "작성 결과 (§9.3)")
public record CommentCreateResponse(Long commentSeq) {
}
