package com.ssafy.motif.coreapi.admin.alias.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 별칭 등록 응답 (어드민 명세 §3.4.6 · §3.5.7) — 201 과 함께 새 seq 만 준다.
 *
 * <p>항목 전체({@link AliasResponse})를 돌려주지 않는다. 화면이 등록 직후 목록을 다시
 * 읽으므로 같은 값을 두 번 실어 보낼 이유가 없고, 등록 응답과 목록 항목을 한 타입으로
 * 묶으면 목록이 칸을 더할 때 등록 응답까지 따라 흔들린다.
 */
@Schema(description = "별칭 등록 응답")
public record AliasCreateResponse(

        @Schema(description = "생성된 별칭 seq", example = "12")
        Integer aliasSeq
) {
}
