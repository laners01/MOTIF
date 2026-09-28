package com.ssafy.motif.coreapi.domain.composite.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/**
 * 합성 잡 생성 응답 (API 명세 §13.1).
 *
 * <p>{@code jobSeq} 를 내보내지 않는다 — 외부에 사는 식별자는 {@code jobUuid} 뿐이다
 * (§1.1, {@code CompositeJob} javadoc). FCM 페이로드에도 이 값만 실린다.
 *
 * <p>{@code uploadUrl} 은 <b>presigned PUT</b> 이다. 클라이언트가 이 주소로 촬영본을 S3 에
 * 직행 업로드하고(동기·로딩 UI) 끝나면 §13.2 를 부른다 — 영상 바이트는 API 서버를
 * 지나지 않는다.
 *
 * @param expiresIn presign 만료까지 남은 초. 지나면 PUT 이 거부되고 §13.1 부터 다시다
 */
@Schema(description = "합성 잡 생성 응답 (§13.1) — 잡 식별자와 원본 업로드 URL")
public record CompositeJobCreateResponse(

        @Schema(description = "잡 식별자 — 이후 §13.2·§13.3·§13.4 와 FCM 이 쓰는 값",
                example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        UUID jobUuid,

        @Schema(description = "촬영 원본 presigned PUT URL — Content-Type 은 video/mp4 고정",
                example = "https://motif-media.s3.ap-northeast-2.amazonaws.com/raw/3fa85f64.mp4?X-Amz-Algorithm=...&X-Amz-Signature=...")
        String uploadUrl,

        @Schema(description = "업로드 URL 만료까지 남은 초", example = "600")
        long expiresIn) {
}
