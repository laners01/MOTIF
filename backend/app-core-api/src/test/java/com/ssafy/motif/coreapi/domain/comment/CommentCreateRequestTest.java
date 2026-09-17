package com.ssafy.motif.coreapi.domain.comment;

import com.ssafy.motif.coreapi.domain.comment.dto.CommentCreateRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 작성 요청의 본문 정규화 (API 명세 §9.3).
 *
 * <p><b>공백 제거가 record 생성자에 있는 것이 핵심이다.</b> Bean Validation 은 record 가
 * 만들어진 <b>뒤</b>에 돌므로, 여기서 떼어 두면 공백만 보낸 요청이 {@code @NotBlank} 에
 * 걸린다. 서비스에서 {@code strip()} 했다면 검증은 이미 통과한 뒤라 공백 한 칸짜리
 * 댓글이 저장된다.
 */
class CommentCreateRequestTest {

    @Test
    @DisplayName("앞뒤 공백을 떼고 받는다")
    void stripsContent() {
        assertThat(new CommentCreateRequest("  잘 봤어요!  ", null).content())
                .isEqualTo("잘 봤어요!");
    }

    @Test
    @DisplayName("공백만 보내면 빈 문자열이 되어 @NotBlank 에 걸린다")
    void blankBecomesEmpty() {
        assertThat(new CommentCreateRequest("   \n\t ", null).content()).isEmpty();
    }

    @Test
    @DisplayName("null 은 그대로 둔다 — 그쪽은 @NotBlank 가 잡는다")
    void keepsNull() {
        assertThat(new CommentCreateRequest(null, null).content()).isNull();
    }
}
