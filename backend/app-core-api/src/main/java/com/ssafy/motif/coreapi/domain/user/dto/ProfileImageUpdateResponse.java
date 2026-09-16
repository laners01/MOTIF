package com.ssafy.motif.coreapi.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 프로필 이미지 반영 결과 (API 명세 §4.4).
 *
 * @param profileImageUrl <b>무서명 공개 CDN URL</b>. 프로필은 공개 등급이라 서명이
 *                        붙지 않는다 — S3 presigned 로 내보내지 않는 이유는 서명
 *                        쿼리스트링이 오리진까지 전달돼 CDN 캐시 적중률을 0% 로 만들기
 *                        때문이다(§1.7 · ADR-0010 D5)
 */
@Schema(description = "프로필 이미지 반영 결과 (§4.4)")
public record ProfileImageUpdateResponse(String profileImageUrl) {
}
