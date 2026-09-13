package com.ssafy.motif.domain.core.artist;

/** 아티스트 seq → 표시 이름({@code users.nickname}) 투영 — {@link ArtistRepository#findNames}. */
public record ArtistName(Integer artistSeq, String name) {
}
