package com.ssafy.motif.coreapi.domain.motif.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * 보관함 항목 (API 명세 §11.5).
 *
 * <p><b>{@code viewCount} 가 없다</b> — 모티프 결과물에는 조회수 카운터가 없다(회의 확정
 * R2 예외). 참여 영상(§7·§8)과 다른 점이라 비어 있는 것이 아니라 <b>없는 개념</b>이다.
 *
 * <p>{@code thumbnailUrl} 은 <b>보호 등급</b>이다(§1.7) — 모티프 결과물은 전량
 * {@code private/} 아래라 CloudFront signed URL 로만 열린다.
 */
@Schema(description = "보관함 항목 (§11.5)")
public record MotifResultItem(

        @Schema(description = "결과물 식별자", example = "10")
        Long resultSeq,

        @Schema(description = "썸네일 URL — **서명된** 보호 등급 주소(§1.7). 만료 6시간")
        String thumbnailUrl,

        @Schema(description = "README §3.5 공통 아티스트 요약")
        Artist artist,

        @Schema(description = "생성 시각 — 원시각이다. \"n시간 전\" 표기는 클라이언트가 만든다")
        Instant regDttm,

        @Schema(description = "대표 모티프 여부 (§4.1 repMotif 와 같은 값)", example = "true")
        boolean isRep) {

    /**
     * README §3.5 공통 아티스트 요약 — 솔로면 {@code groupName} 이 {@code null} 이다.
     *
     * <p>모양이 같은 record 가 §11.1·§11.3 에도 있지만 합치지 않는다 — 화면마다 별개
     * 티켓으로 자라는 응답이라 한 타입으로 묶으면 한쪽 요구가 다른 쪽 계약을 흔든다.
     */
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
