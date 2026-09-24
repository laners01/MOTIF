package com.ssafy.motif.coreapi.domain.asset.dto;

import com.ssafy.motif.domain.core.asset.AssetType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/**
 * 숍 한 섹션 — 구독 아티스트 하나와 그 아티스트의 에셋 전량 (API 명세 §11.3).
 *
 * <p>클라이언트가 <b>받은 순서 그대로</b> 섹션을 그린다. 아티스트는 구독 최신순,
 * 에셋은 최신순이다.
 *
 * <p><b>에셋이 0건이어도 섹션은 나온다.</b> 빼 버리면 "구독했는데 숍에 없는 아티스트"
 * 가 생겨 §4.7 구독 목록과 어긋난다.
 */
@Schema(description = "숍 섹션 — 구독 아티스트 + 에셋 전량 (§11.3)")
public record ShopSectionItem(

        @Schema(description = "README §3.5 공통 아티스트 요약")
        Artist artist,

        @Schema(description = "이 아티스트의 유효한 패스를 보유 중인지", example = "true")
        boolean passActive,

        @Schema(description = "패스 만료 시각. 미보유면 null", nullable = true)
        Instant expiresDttm,

        @Schema(description = "이 아티스트의 에셋 전량. 없으면 빈 배열")
        List<Asset> assets) {

    /** README §3.5 공통 아티스트 요약 — 솔로면 {@code groupName} 이 {@code null} 이다. */
    @Schema(description = "아티스트 요약 (README §3.5)")
    public record Artist(

            @Schema(description = "아티스트 식별자", example = "3")
            Integer artistSeq,

            @Schema(description = "아티스트 이름 — users.nickname", example = "카리나")
            String name,

            @Schema(description = "그룹명. 솔로면 null", example = "aespa")
            String groupName,

            @Schema(description = "프로필 이미지 URL. 미설정이면 기본 프로필 이미지")
            String profileImageUrl) {
    }

    /**
     * 섹션 안의 에셋 한 건.
     *
     * <p><b>{@code artist} 를 다시 싣지 않는다</b> — 섹션이 이미 아티스트를 말하고 있다.
     * 항목마다 붙이는 §11.1·§11.2 와 다른 점이다.
     *
     * <p><b>가격이 없다.</b> 가격과 구매 가능 여부는 §11.2 상세의 몫이다.
     */
    @Schema(description = "숍 섹션 안의 에셋")
    public record Asset(

            @Schema(description = "에셋 식별자", example = "21")
            Integer assetSeq,

            @Schema(description = "에셋 표시명", example = "9월 신규 에셋")
            String title,

            @Schema(description = "썸네일 URL — 공개 CDN")
            String thumbnailUrl,

            @Schema(description = "소개 문구. 없으면 null", example = "9월 신규 에셋 소개 문구")
            String description,

            @Schema(description = "길이(ms)", example = "9800")
            Integer durationMs,

            @Schema(description = "에셋 종류", example = "NORMAL")
            AssetType assetType,

            @Schema(description = "내 이용 권한 — OWNED > PASS > NONE. 구매 여부가 여기 있다",
                    example = "OWNED")
            MyAccess myAccess) {
    }
}
