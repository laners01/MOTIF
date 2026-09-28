package com.ssafy.motif.coreapi.admin.media.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 업로드 URL 발급 응답 (어드민 명세 §3.2.1).
 *
 * @param objectKey 등록 API 에 그대로 넘길 S3 키 (Claim-Check)
 * @param expiresIn 만료까지 남은 초 — 영상 600 · 이미지 300 (§1.4)
 */
@Schema(description = "어드민 업로드 presign 응답")
public record PresignResponse(String uploadUrl, String objectKey, long expiresIn) {
}
