package com.ssafy.motif.coreapi.domain.artist.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 명세 §5.4 {@code data.list[]} — 아티스트 챌린지 그리드 한 칸.
 *
 * <p><b>그리드 경량 + 조회수뿐이다</b> — {@code title} 은 없다(R3 에서
 * {@code challenges.title} 컬럼 자체가 삭제됐다). 재생에 필요한 {@code videoUrl}·
 * {@code durationMs} 도 없다: 그리드는 썸네일만 깔고, 탭하면 §7.2 상세로 간다.
 * 목록에 재생 정보를 미리 실으면 스크롤 한 번에 쓰지도 않을 필드를 수십 건 나른다.
 */
public record ArtistChallengeResponse(
        Integer challengeSeq,

        @Schema(description = "CloudFront 일반 URL(공개 등급 — §1.7). 미설정이면 null", nullable = true)
        String thumbnailUrl,

        @Schema(description = "누적 조회수", example = "120000")
        long viewCount
) {
}
