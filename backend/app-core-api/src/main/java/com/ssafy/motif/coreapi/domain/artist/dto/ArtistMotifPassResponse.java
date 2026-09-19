package com.ssafy.motif.coreapi.domain.artist.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/**
 * 아티스트별 모티프 패스 (API 명세 §11.4).
 *
 * <p>패스 구매 화면 하나를 그리는 데 필요한 것을 모았다 — 배너 · 내 보유 상태 · 지금
 * 파는 가격.
 *
 * <p>{@code ArtistDetailResponse.MotifPass}(§5.3)와 겹쳐 보이지만 <b>답하는 질문이
 * 다르다</b>. 저쪽은 아티스트 상세 한 칸에서 "패스가 있나"만 말하고, 여기는 "없으면
 * 얼마에 살 수 있나"까지 답한다. 판정 자체는
 * {@code MotifPassRepository.findActivePassExpiry} 한 곳이라 두 벌이 아니다.
 */
@Schema(description = "아티스트별 모티프 패스 (§11.4)")
public record ArtistMotifPassResponse(

        @Schema(description = "아티스트 식별자", example = "3")
        Integer artistSeq,

        @Schema(description = "패스 화면 배경 — artists.motif_bg_image_key. 미설정이면 null",
                nullable = true)
        String bannerImageUrl,

        @Schema(description = "유효한 패스를 보유 중인지", example = "false")
        boolean passActive,

        @Schema(description = "패스 만료 시각. 미보유면 null", nullable = true)
        Instant expiresDttm,

        @Schema(description = "판매 중인 개월 수별 가격. 보유 중이면 빈 배열")
        List<Price> prices) {

    /**
     * 미보유 응답 — {@code prices} 는 호출부가 채운다.
     */
    public static ArtistMotifPassResponse available(Integer artistSeq, String bannerImageUrl,
                                                    List<Price> prices) {
        return new ArtistMotifPassResponse(artistSeq, bannerImageUrl, false, null, prices);
    }

    /**
     * 보유 중 응답 — <b>{@code prices} 는 언제나 빈 배열이다</b>.
     *
     * <p>현재 구현은 만료 전 연장 구매를 지원하지 않는다(§11.4). 필드를 빼지 않는 것은
     * 클라이언트가 두 응답에 같은 타입을 쓰기 위해서다.
     */
    public static ArtistMotifPassResponse held(Integer artistSeq, String bannerImageUrl,
                                               Instant expiresDttm) {
        return new ArtistMotifPassResponse(artistSeq, bannerImageUrl, true, expiresDttm, List.of());
    }

    /**
     * 개월 수별 가격.
     *
     * <p><b>{@code amount} 는 문자열이다.</b> {@code NUMERIC(10,2)} 를 부동소수로 실어
     * 보내면 클라이언트 언어에 따라 금액이 계산 전에 이미 틀어진다(§11.2 와 같은 계약).
     */
    @Schema(description = "개월 수별 가격 — amount 는 문자열")
    public record Price(

            @Schema(description = "개월 수", example = "1")
            short durationMonths,

            @Schema(description = "금액 — 문자열", example = "9900.00")
            String amount,

            @Schema(description = "통화", example = "KRW")
            String currency) {
    }
}
