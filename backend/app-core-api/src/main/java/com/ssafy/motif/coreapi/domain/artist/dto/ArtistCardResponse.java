package com.ssafy.motif.coreapi.domain.artist.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 전체 아티스트 목록 한 줄 (API 명세 §5.10) — 아티스트 요약(상세 명세 README §3.5) +
 * {@code isSubscribed}.
 *
 * <p>봉투는 {@code ListResponse} 다 — 전량 반환이라 페이지 칸이 없다.
 *
 * <p><b>{@code subscriberCount} 를 싣지 않는다.</b> 정렬 기준일 뿐 화면이 숫자를 쓰지
 * 않는다(§5.10). 개별 아티스트의 구독자 수는 §5.3 상세가 준다.
 *
 * <p><b>{@link ArtistRankingResponse} 와 합치지 않는다.</b> 다섯 칸이 겹치지만 랭킹은
 * {@code rank}·{@code rankDelta} 를 더 갖고 그 두 값의 출처가 Redis 스냅샷이다 — 합쳐
 * 두면 이 목록이 쓰지도 않을 두 칸에 {@code null}·0 을 밀어 넣어야 한다.
 *
 * <p>§5.2 검색·§5.9 자동완성이 붙을 때 모양이 같다면 그때 공유를 판단한다. 지금 선점해
 * 두면 그 티켓이 칸을 더하는 순간 이 응답이 함께 흔들린다.
 */
@Schema(description = "전체 아티스트 목록 항목 (§5.10)")
public record ArtistCardResponse(

        @Schema(description = "아티스트 seq", example = "3")
        Integer artistSeq,

        @Schema(description = "아티스트 이름 (users.nickname)", example = "카리나")
        String name,

        @Schema(description = "그룹명. 솔로 아티스트는 null", example = "aespa", nullable = true)
        String groupName,

        @Schema(description = "프로필 이미지 URL — 공개 등급이라 서명하지 않는다(§1.7). 미등록이면 null",
                example = "https://cdn.motif.io/profiles/3f9c1a2b.jpg", nullable = true)
        String profileImageUrl,

        @Schema(description = "요청자의 구독 여부", example = "true")
        boolean isSubscribed
) {
}
