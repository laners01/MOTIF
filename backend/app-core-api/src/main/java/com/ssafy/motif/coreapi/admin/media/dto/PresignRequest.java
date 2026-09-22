package com.ssafy.motif.coreapi.admin.media.dto;

import com.ssafy.motif.coreapi.admin.media.AdminMediaKind;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 업로드 URL 발급 요청 (어드민 명세 §3.2.1).
 *
 * <p>{@code EDITED_*} 는 키가 슬롯에 매여 있어 두 seq 를 <b>함께</b> 받는다. 편집본은
 * 챌린지·슬롯이 이미 있는 상태에서만 올라오므로(등록 §3.7.2 → 편집본 §3.7.6) 둘 다
 * 정해져 있다 — {@code pending} 자리표시자가 없어진 것이 그 때문이다.
 *
 * @param challengeSeq            {@code EDITED_*} 필수. 그 밖의 {@code kind} 에서는 무시한다
 * @param challengeParticipantSeq {@code EDITED_*} 필수 — §7.5·§3.7.6 이 쓰는 슬롯 seq 와
 *                                같은 값이다. 그 밖의 {@code kind} 에서는 무시한다
 */
@Schema(description = "어드민 업로드 presign 요청")
public record PresignRequest(

        @Schema(description = "업로드 종류 — kind 가 프리픽스·확장자·크기 상한을 정한다",
                example = "EDITED_VIDEO")
        @NotNull AdminMediaKind kind,

        @Schema(description = "올릴 파일의 Content-Type. 서명에 박히므로 PUT 과 같아야 한다",
                example = "video/mp4")
        @NotBlank String contentType,

        @Schema(description = "올릴 파일의 바이트 수. 서명에 박히므로 PUT 의 Content-Length 와 정확히 같아야 한다",
                example = "48213377")
        @Positive long contentLength,

        @Schema(description = "EDITED_* 필수 — 대상 챌린지", example = "11")
        @Positive Integer challengeSeq,

        @Schema(description = "EDITED_* 필수 — 대상 슬롯(§7.5 가 준 challengeParticipantSeq)",
                example = "31")
        @Positive Integer challengeParticipantSeq) {
}
