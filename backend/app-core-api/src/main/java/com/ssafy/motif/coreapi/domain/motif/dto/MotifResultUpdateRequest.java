package com.ssafy.motif.coreapi.domain.motif.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * 결과물 수정 요청 (API 명세 §11.8) — <b>부분 수정</b>.
 *
 * <p>둘 다 선택이다. <b>{@code null} 은 "보내지 않았다"</b> 이고 보낸 필드만 반영한다.
 *
 * @param description 결과물 문구. <b>빈 문자열이 "지움"</b> 이라 {@code null} 과 다르다.
 *                    최초 입력과 이후 수정 모두 이 필드다 — 합성 직후에는 {@code NULL}
 *                    이다(§11.7). DB 는 TEXT 지만 상한을 둔다. 없으면 본문 크기 제한이
 *                    서버 전역 설정에만 걸린다(§8.9 와 같은 값)
 * @param isRep       {@code true} 면 대표 모티프로 지정, {@code false} 면 해제.
 *                    <b>대표는 사용자당 하나</b>라 지정은 곧 이전 대표의 해제다
 */
@Schema(description = "결과물 수정 요청 (§11.8) — 보낸 필드만 반영한다")
public record MotifResultUpdateRequest(

        @Schema(description = "결과물 문구. 빈 문자열이면 지운다", example = "첫 모티프")
        @Size(max = 1000)
        String description,

        @Schema(description = "대표 모티프 여부", example = "true")
        Boolean isRep) {

    /** 둘 다 안 보냈는가 — 이때는 아무것도 바꾸지 않고 현재 상태를 돌려준다(§8.9 와 같다). */
    public boolean isEmpty() {
        return description == null && isRep == null;
    }
}
