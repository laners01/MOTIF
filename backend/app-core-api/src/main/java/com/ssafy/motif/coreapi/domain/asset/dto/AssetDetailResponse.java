package com.ssafy.motif.coreapi.domain.asset.dto;

import com.ssafy.motif.domain.core.asset.AssetType;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 에셋 상세 (API 명세 §11.2).
 *
 * <p>목록(§11.1)이 주지 않는 세 가지가 여기 있다 — 미리보기 영상 · 가격 · 구매 가능 여부.
 *
 * <p>{@code AssetCatalogItem} 과 합치지 않는다. 겹치는 칸이 많지만 목록은 가격 조인 없이
 * 수십 건이 오는 계약이고, 한 record 로 묶으면 목록 항목에 언제나 {@code null} 인
 * {@code price}·{@code previewVideoUrl} 이 붙는다.
 */
@Schema(description = "에셋 상세 (§11.2)")
public record AssetDetailResponse(

        @Schema(description = "에셋 식별자", example = "21")
        Integer assetSeq,

        @Schema(description = "README §3.5 공통 아티스트 요약")
        Artist artist,

        @Schema(description = "에셋 표시명", example = "9월 신규 에셋")
        String title,

        @Schema(description = "미리보기 영상 URL — 공개 CDN 의 HLS 마스터 재생목록(§1.7)")
        String previewVideoUrl,

        @Schema(description = "썸네일 URL — 공개 CDN")
        String thumbnailUrl,

        @Schema(description = "소개 문구. 없으면 null", example = "9월 신규 에셋 소개 문구")
        String description,

        @Schema(description = "길이(ms)", example = "9800")
        Integer durationMs,

        @Schema(description = "에셋 종류", example = "NORMAL")
        AssetType assetType,

        @Schema(description = "KR 활성 가격. 없으면 null")
        Price price,

        @Schema(description = "지금 구매할 수 있는가", example = "true")
        boolean purchasable,

        @Schema(description = "내 이용 권한 — OWNED > PASS > NONE", example = "NONE")
        MyAccess myAccess) {

    /** README §3.5 공통 아티스트 요약 — {@code AssetCatalogItem.Artist} 와 모양만 같다. */
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
     * 가격.
     *
     * <p><b>{@code amount} 는 문자열이다.</b> {@code NUMERIC(10,2)} 를 부동소수로 실어
     * 보내면 클라이언트 언어에 따라 {@code 3900.00} 이 {@code 3899.9999…} 로 읽힌다 —
     * 금액은 계산 전에 이미 틀리면 안 되는 값이다.
     */
    @Schema(description = "가격 — amount 는 문자열")
    public record Price(

            @Schema(description = "금액 — 문자열", example = "3900.00")
            String amount,

            @Schema(description = "통화", example = "KRW")
            String currency) {
    }
}
