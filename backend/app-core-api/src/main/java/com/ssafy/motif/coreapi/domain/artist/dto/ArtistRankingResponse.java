package com.ssafy.motif.coreapi.domain.artist.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 아티스트 랭킹 한 줄 (API 명세 §5.1) — 홈 Who's On TOP4(§6.2)·온보딩 인기 목록(§3.1) 공용.
 *
 * <p>봉투는 {@code PageResponse} 다(§1.2 offset).
 */
@Schema(description = "아티스트 랭킹 항목 (§5.1)")
public record ArtistRankingResponse(

        @Schema(description = "스냅샷 순위(1-base). filter 로 걸러도 다시 매기지 않는다 — 전체 기준이다",
                example = "1")
        int rank,

        @Schema(description = """
                직전 스냅샷 순위 − 현재 순위. 상승이 양수·하락이 음수(5위→3위 = 2, 3위→5위 = -2).
                **신규 진입은 null**""", example = "2", nullable = true)
        Integer rankDelta,

        @Schema(description = "아티스트 seq", example = "3")
        Integer artistSeq,

        @Schema(description = "아티스트 이름 (users.nickname)", example = "카리나")
        String name,

        @Schema(description = "그룹명. 솔로 아티스트는 null", example = "aespa", nullable = true)
        String groupName,

        @Schema(description = "프로필 이미지 URL — 공개 등급이라 서명하지 않는다(§1.7). 미등록이면 기본 프로필 이미지",
                example = "https://cdn.motif.io/profiles/3f9c1a2b.jpg")
        String profileImageUrl,

        @Schema(description = "요청자의 구독 여부", example = "false")
        boolean isSubscribed,

        @Schema(description = "대표곡 제목 — 가장 최근 챌린지의 곡. 챌린지가 없으면 null",
                example = "Supernova", nullable = true)
        String songTitle,

        @Schema(description = "대표곡 가수명 (songs.singer_name). 챌린지가 없으면 null",
                example = "aespa", nullable = true)
        String singerName
) {
}
