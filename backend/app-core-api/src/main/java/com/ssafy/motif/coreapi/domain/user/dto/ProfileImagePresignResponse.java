package com.ssafy.motif.coreapi.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 프로필 이미지 업로드 URL (API 명세 §4.3).
 *
 * @param uploadUrl S3 presigned <b>PUT</b> URL. 이 주소로 이미지 바이트를 직접 올린다 —
 *                  바이트는 API 서버를 통과하지 않는다(설계 원칙 ①)
 * @param imageKey  서버가 발급한 키. 업로드를 마친 뒤 <b>§4.4 에 그대로 전달</b>한다
 * @param expiresIn 업로드 URL 유효 시간(초). 이 시간이 지나면 발급 기록도 함께
 *                  사라져 §4.4 가 403 이다
 */
@Schema(description = "프로필 이미지 업로드 URL (§4.3)")
public record ProfileImagePresignResponse(String uploadUrl, String imageKey, long expiresIn) {
}
