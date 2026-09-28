package com.ssafy.motif.coreapi.admin.motif.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 모티프 결과물 썸네일 교체 (어드민 명세 §3.17.2) — {@code MOTIF_THUMBNAIL} 로 올린 키. */
public record MotifThumbnailRequest(@NotBlank @Size(max = 255) String thumbnailKey) {
}
