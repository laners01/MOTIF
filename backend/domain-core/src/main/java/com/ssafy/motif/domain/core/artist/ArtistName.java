package com.ssafy.motif.domain.core.artist;

/**
 * 아티스트 seq → 표시 이름·프로필 키 투영 — {@link ArtistRepository#findNames}.
 *
 * <p>둘 다 {@code users} 의 값이다({@code nickname}·{@code profile_image_key}) —
 * 아티스트의 이름과 얼굴은 아티스트 행이 아니라 사용자 행에 있다.
 *
 * @param profileImageKey S3 키. URL 변환은 응답 조립 시점에 호출부가 한다
 *                        (공개 등급이면 {@code PublicMediaUrlResolver})
 */
public record ArtistName(Integer artistSeq, String name, String profileImageKey) {
}
