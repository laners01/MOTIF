package com.ssafy.motif.coreapi.admin.asset.dto;

import com.ssafy.motif.domain.core.asset.AssetType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * 에셋 목록 항목 (어드민 명세 §3.8.1).
 *
 * @param price         KR 가격. <b>{@code null} 이면 그 에셋은 팔리지 않는다</b> — 화면이
 *                      "가격 미설정"을 띄우는 근거다(§3.8.1)
 * @param purchaseCount {@code ACTIVE} 구매 수 · {@code resultCount} 비삭제 결과물 수 —
 *                      운영 지표라 에셋을 내릴지 판단할 때 본다
 */
@Schema(description = "모티프 에셋 목록 항목")
public record AssetListItem(
        Integer assetSeq,
        ArtistSummary artist,
        String title,
        String thumbnailUrl,
        int durationMs,
        AssetType assetType,
        boolean isActive,
        AssetPriceResponse price,
        long purchaseCount,
        long resultCount,
        Instant regDttm) {

    /** 챌린지 쪽 요약과 달리 {@code groupName} 을 갖는다(§3.8.1) — 그룹 미소속이면 {@code null}. */
    public record ArtistSummary(Integer artistSeq, String name, String groupName) {
    }
}
