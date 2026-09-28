package com.ssafy.motif.coreapi.admin.asset.dto;

/**
 * 에셋 등록 성공 (어드민 명세 §3.8.2, 201).
 *
 * @param thumbnailUrl 등록 직후에는 <b>아직 없는 파일</b>을 가리킨다 — app-media 가
 *                     만들 때까지 404 다(§1.4 3항, §8 #19)
 */
public record AssetCreateResponse(Integer assetSeq, long durationMs, String thumbnailUrl) {
}
