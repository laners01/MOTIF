package com.ssafy.motif.coreapi.domain.asset.dto;

import com.ssafy.motif.domain.core.asset.AssetType;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 내 보유함 항목 (API 명세 §11.1) — <b>내가 구매한 에셋</b>.
 *
 * <p><b>가격이 없다.</b> 가격과 구매 가능 여부는 §11.2 상세의 몫이다.
 *
 * <p><b>{@code myAccess} 도 없다.</b> 전부 개별 구매라 값이 {@code OWNED} 하나뿐인 칸이
 * 된다 — 그 판정이 의미를 갖는 곳은 §11.2 상세와 §11.3 숍이다(S15P21D104-219).
 *
 * <p>항목마다 {@code artist} 요약이 붙는다. 여러 아티스트에게서 산 것이 한 목록에
 * 섞이므로 클라이언트가 이 값으로 묶는다 — 섹션이 이미 아티스트를 말하는 §11.3 과 다른
 * 점이다.
 */
@Schema(description = "내 보유함 항목 — 구매한 에셋 (§11.1)")
public record AssetCatalogItem(

        @Schema(description = "에셋 식별자", example = "21")
        Integer assetSeq,

        @Schema(description = "README §3.5 공통 아티스트 요약")
        Artist artist,

        @Schema(description = "에셋 표시명", example = "9월 신규 에셋")
        String title,

        @Schema(description = "썸네일 URL — 공개 CDN")
        String thumbnailUrl,

        @Schema(description = "소개 문구. 없으면 null", example = "9월 신규 에셋 소개 문구")
        String description,

        @Schema(description = "길이(ms)", example = "9800")
        Integer durationMs,

        @Schema(description = "에셋 종류", example = "NORMAL")
        AssetType assetType) {

    /**
     * README §3.5 공통 아티스트 요약 — 솔로면 {@code groupName} 이 {@code null} 이다.
     *
     * <p>{@code ActivityItem.Artist}·{@code SubscriptionCardItem} 과 <b>모양만</b> 같고
     * 타입을 공유하지 않는다. 화면마다 별개 티켓으로 자라는 응답이라 한 record 로 묶으면
     * 한쪽 요구가 다른 쪽 계약을 흔든다.
     */
    @Schema(description = "아티스트 요약 (README §3.5)")
    public record Artist(

            @Schema(description = "아티스트 식별자", example = "3")
            Integer artistSeq,

            @Schema(description = "아티스트 이름 — users.nickname", example = "카리나")
            String name,

            @Schema(description = "그룹명. 솔로면 null", example = "aespa")
            String groupName,

            @Schema(description = "프로필 이미지 URL. 미설정이면 null")
            String profileImageUrl) {
    }
}
