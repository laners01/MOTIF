package com.ssafy.motif.coreapi.domain.video.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 게시 응답 (API 명세 §8.8) — <b>201 Created</b>.
 *
 * <p>seq 하나뿐이다. 클라이언트는 이 값으로 전체 화면 뷰어(§8.2)로 이동한다.
 */
@Schema(description = "게시 응답 (§8.8)")
public record VideoPublishResponse(

        @Schema(description = "만들어진 참여 영상 seq", example = "88")
        Long videoSeq) {
}
