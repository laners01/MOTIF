package com.ssafy.motif.coreapi.domain.video.dto;

import com.ssafy.motif.domain.core.video.Visibility;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * 게시 정보 수정 요청 (API 명세 §8.9) — <b>부분 수정</b>.
 *
 * <p>세 필드 모두 선택이다. <b>{@code null} 은 "보내지 않았다"</b> 이고, 보낸 필드만
 * 반영한다. 제목은 개념 자체가 없다(R3 — 컬럼이 삭제됐다).
 *
 * @param description    빈 문자열이 "문구 지움" 이다 — {@code null} 과 다르다.
 *                       DB 는 TEXT 지만 상한을 둔다. 없으면 본문 크기 제한이 서버 전역
 *                       설정에만 걸린다
 * @param visibility     {@code PUBLIC} | {@code PRIVATE}. 표에 없는 값은 400 이다
 * @param commentEnabled {@code false} 면 댓글이 <b>동결</b>된다 — 목록 조회까지
 *                       막힌다(§9)
 */
@Schema(description = "게시 정보 수정 요청 (§8.9) — 보낸 필드만 반영한다")
public record VideoUpdateRequest(

        @Schema(description = "설명. 빈 문자열이면 지운다", example = "수정한 문구")
        @Size(max = 1000)
        String description,

        @Schema(description = "PUBLIC | PRIVATE")
        Visibility visibility,

        @Schema(description = "댓글 허용 여부")
        Boolean commentEnabled
) {

    /** 셋 다 안 보냈는가 — 이때는 아무것도 바꾸지 않고 현재 상태를 돌려준다. */
    public boolean isEmpty() {
        return description == null && visibility == null && commentEnabled == null;
    }
}
