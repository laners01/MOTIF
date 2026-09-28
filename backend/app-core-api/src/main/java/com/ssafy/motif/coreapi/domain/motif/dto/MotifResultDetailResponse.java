package com.ssafy.motif.coreapi.domain.motif.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * 결과물 상세 (API 명세 §11.6) — 재생 화면.
 *
 * <p>목록({@code MotifResultItem})이 주지 않는 것이 셋이다 — 재생 주소 · 문구 · 에셋.
 * 합치지 않는 이유는 목록이 한 번에 수십 건 오는 계약이기 때문이다.
 *
 * <p><b>두 URL 의 서명이 다르다</b>(ADR-0012). {@code videoUrl} 은 조각까지 통하는
 * 프리픽스 서명이고, {@code thumbnailUrl} 은 그 한 장짜리 서명이다.
 */
@Schema(description = "결과물 상세 (§11.6)")
public record MotifResultDetailResponse(

        @Schema(description = "결과물 식별자", example = "10")
        Long resultSeq,

        @Schema(description = "HLS 마스터 재생목록 — **프리픽스 서명**이 붙는다. 조각 요청에도 "
                + "같은 파라미터를 붙여야 한다(ADR-0012)")
        String videoUrl,

        @Schema(description = "썸네일 URL — 단일 파일 서명(만료 6시간)")
        String thumbnailUrl,

        @Schema(description = "결과물 문구. 합성 직후에는 null 이고 §11.8 로 채운다",
                nullable = true)
        String description,

        @Schema(description = "길이(ms)", example = "9800")
        Integer durationMs,

        @Schema(description = "이 결과물을 만든 에셋")
        Asset asset,

        @Schema(description = "README §3.5 공통 아티스트 요약")
        Artist artist,

        @Schema(description = "대표 모티프 여부 (§4.1 repMotif 와 같은 값)", example = "false")
        boolean isRep,

        @Schema(description = "생성 시각 — 원시각이다")
        Instant regDttm) {

    /**
     * 에셋 요약 — <b>두 칸뿐이다</b>(명세 §11.6).
     *
     * <p>썸네일·가격을 더하지 않는다. 재생 화면이 필요로 하는 것은 "무엇으로 만들었나"
     * 이고, 그 이상은 §11.2 상세로 들어간다.
     */
    @Schema(description = "에셋 요약")
    public record Asset(

            @Schema(description = "에셋 식별자", example = "21")
            Integer assetSeq,

            @Schema(description = "에셋 표시명", example = "9월 신규 에셋")
            String title) {
    }

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
}
