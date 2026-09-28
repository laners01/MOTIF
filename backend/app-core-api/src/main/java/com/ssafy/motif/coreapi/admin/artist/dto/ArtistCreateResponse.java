package com.ssafy.motif.coreapi.admin.artist.dto;

/** 아티스트 등록 성공 (어드민 명세 §3.5.2, 201) — 두 행이 한 트랜잭션에 생겼다는 증거다. */
public record ArtistCreateResponse(Integer artistSeq, Integer userSeq) {
}
