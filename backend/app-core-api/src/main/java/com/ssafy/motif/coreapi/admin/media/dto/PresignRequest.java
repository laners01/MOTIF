package com.ssafy.motif.coreapi.admin.media.dto;

import com.ssafy.motif.coreapi.admin.media.AdminMediaKind;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 업로드 URL 발급 요청 (어드민 명세 §3.2.1).
 *
 * @param challengeSeq {@code EDITED_*} 에서만 쓴다. 챌린지 신규 등록 중이라 아직 seq 가
 *                     없으면 생략 — 서버가 {@code edited/pending/{uuid}} 로 발급한다
 */
@Schema(description = "어드민 업로드 presign 요청")
public record PresignRequest(
        @NotNull AdminMediaKind kind,
        @NotBlank String contentType,
        @Positive long contentLength,
        Integer challengeSeq) {
}
