package com.ssafy.motif.coreapi.admin.asset.dto;

import com.ssafy.motif.domain.core.asset.AssetType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * 에셋 상세 (어드민 명세 §3.8.3). 수정(§3.8.4)의 응답도 이 형태다.
 *
 * <p>목록 항목(§3.8.1)과 같은 값에 {@code description} 하나가 더 붙는다. 목록에 그
 * 필드를 넣지 않는 이유는 소개 문구가 길고 한 페이지 100행에 그대로 곱해지기 때문이다.
 *
 * <p><b>{@code videoKey} 를 내려준다</b> — 목록에는 없는 값이다. 수정 화면이 "영상을
 * 바꾸지 않았다" 를 표현하려면 지금 키를 알아야 한다. 공개 CDN 경로가 아니라 S3 키라
 * 그 자체로는 재생할 수 없다(미리보기는 {@code thumbnailUrl} 로 한다).
 */
@Schema(description = "모티프 에셋 상세")
public record AssetDetailResponse(
        Integer assetSeq,
        AssetListItem.ArtistSummary artist,
        String title,
        String description,
        String thumbnailUrl,
        String videoKey,
        int durationMs,
        AssetType assetType,
        boolean isActive,
        AssetPriceResponse price,
        long purchaseCount,
        long resultCount,
        Instant regDttm) {
}
