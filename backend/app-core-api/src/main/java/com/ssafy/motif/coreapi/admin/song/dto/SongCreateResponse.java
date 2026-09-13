package com.ssafy.motif.coreapi.admin.song.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 곡 등록 결과 (어드민 명세 §3.6.2 — 201). */
@Schema(description = "곡 등록 결과")
public record SongCreateResponse(Integer songSeq) {
}
