package com.ssafy.motif.coreapi.domain.video.dto;

import com.ssafy.motif.domain.core.video.Visibility;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * 게시 요청 (API 명세 §8.8).
 *
 * <p><b>{@code title} 은 없다</b>(R3 — 컬럼이 삭제됐다). 제목은 개념 자체가 없다.
 *
 * <p><b>바이트를 보내지 않는다.</b> 촬영본은 §13.1 presign 으로 이미 S3 에 있고 합성
 * 산출물도 마찬가지다 — 게시는 그것을 가리키는 행을 만드는 일이다.
 *
 * @param jobUuid        {@code jobType=CHALLENGE} 이고 {@code DONE} 인 <b>본인</b> 잡
 * @param description    상한 1000자 — §8.9 수정과 같은 값이어야 한다(DB 는 TEXT 라 무제한)
 * @param visibility     {@code PUBLIC} | {@code PRIVATE}. <b>앱 안의 공개 범위일 뿐</b>이고
 *                       전송 등급은 서명이 맡는다(ADR-0013) — 이 값으로 키가 움직이지 않는다
 * @param commentEnabled 댓글 허용 여부
 */
@Schema(description = "게시 요청 (§8.8) — 합성이 끝난 잡을 참여 영상으로 만든다")
public record VideoPublishRequest(

        @Schema(description = "합성 잡 식별자", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        @NotNull
        UUID jobUuid,

        @Schema(description = "게시 문구 — 1000자 이하", example = "재밌었다!")
        @NotBlank
        @Size(max = 1000)
        String description,

        @Schema(description = "공개 범위", example = "PUBLIC")
        @NotNull
        Visibility visibility,

        @Schema(description = "댓글 허용 여부", example = "true")
        @NotNull
        Boolean commentEnabled) {
}
