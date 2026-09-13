package com.ssafy.motif.coreapi.admin.asset.dto;

import jakarta.validation.constraints.NotNull;

/** 에셋 활성/비활성 전환 요청 (어드민 명세 §3.8.5). */
public record AssetActiveRequest(@NotNull Boolean isActive) {
}
