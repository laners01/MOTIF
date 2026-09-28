package com.ssafy.motif.coreapi.domain.comment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 댓글·답글 작성 요청 (API 명세 §9.3).
 *
 * @param content          본문. <b>(기본안) 최대 1000자</b> — 명세가 길이를 비워 뒀고
 *                         DB 는 {@code TEXT} 라 상한이 없다. 상한이 없으면 한 건으로
 *                         목록 응답을 통째로 부풀릴 수 있다
 * @param parentCommentSeq 답글일 때만. <b>루트 댓글이면 보내지 않는다.</b> 값이 있으면
 *                         그것이 루트인지·같은 게시물의 댓글인지 서버가 확인한다
 */
@Schema(description = "댓글·답글 작성 요청 (§9.3)")
public record CommentCreateRequest(

        @Schema(description = "본문 — 최대 1000자", example = "잘 봤어요!")
        @NotBlank
        @Size(max = 1000)
        String content,

        @Schema(description = "답글일 때만. 루트 댓글은 생략한다", example = "501")
        Long parentCommentSeq
) {

    /**
     * 앞뒤 공백을 떼고 받는다.
     *
     * <p><b>검증보다 먼저 일어난다</b> — record 가 만들어진 뒤에 Bean Validation 이
     * 돌기 때문에, 공백만 보낸 요청은 여기서 빈 문자열이 되어 {@code @NotBlank} 에
     * 걸린다. 서비스에서 {@code strip()} 하면 검증은 이미 통과한 뒤다.
     */
    public CommentCreateRequest {
        content = content == null ? null : content.strip();
    }
}
