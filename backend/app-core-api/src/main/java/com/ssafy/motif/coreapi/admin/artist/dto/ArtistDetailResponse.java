package com.ssafy.motif.coreapi.admin.artist.dto;

import com.ssafy.motif.coreapi.admin.pass.dto.PassPriceResponse;

import java.util.List;

/**
 * 아티스트 상세 (어드민 명세 §3.5.3) — 목록 항목 + {@code bio}·배경 이미지·패스 가격·
 * 최신 챌린지 5건.
 *
 * <p>{@code passPrices} 는 §3.9 와 같은 모양이다 — 가격표 서비스를 그대로 부른다
 * (형식을 두 번 정의하면 한쪽만 고쳐질 자리가 생긴다).
 */
public record ArtistDetailResponse(
        ArtistSummaryResponse summary,
        String bio,
        String motifBgImageUrl,
        List<PassPriceResponse> passPrices,
        List<RecentChallenge> recentChallenges
) {

    /** 최신 5건 경량 — 화면이 목록으로 넘어갈 링크만 필요하다. */
    public record RecentChallenge(Integer challengeSeq, String description, String thumbnailUrl) {
    }
}
