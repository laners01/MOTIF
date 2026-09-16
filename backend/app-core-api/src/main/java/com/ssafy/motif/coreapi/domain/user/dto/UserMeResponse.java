package com.ssafy.motif.coreapi.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 마이페이지 원콜 응답 (API 명세 §4.1).
 *
 * <p>프로필 + 대표 모티프 + 내 영상 3×3 프리뷰를 한 번에 담는다. 전체 목록·더보기는
 * §4.8 이고, 대표 모티프의 설정·해제·문구 수정은 §11.8 이다(R4 통합).
 *
 * @param profileImageUrl 공개 CDN URL. 미설정이면 {@code null}
 * @param repMotif        대표 모티프. <b>미설정·삭제·부재면 {@code null}</b>
 * @param videos          내 참여 영상 최신 9건. <b>없으면 빈 배열</b>
 */
@Schema(description = "마이페이지 원콜 (§4.1)")
public record UserMeResponse(
        Integer userSeq,
        String nickname,
        @Schema(description = "공개 CDN URL. 미설정이면 null", nullable = true)
        String profileImageUrl,
        @Schema(description = "대표 모티프. 미설정·삭제·부재면 null", nullable = true)
        RepMotif repMotif,
        @Schema(description = "내 참여 영상 최신 9건. 없으면 []. 전량은 §4.8")
        List<VideoPreviewItem> videos) {

    /**
     * 대표 모티프 — 마이페이지 진입 시 자동재생·음소거.
     *
     * @param videoUrl     <b>HLS 마스터 재생목록</b> 주소. 모티프 결과물은 보호 등급이라
     *                     CDN 이 아니라 <b>core-api</b> 를 가리킨다(§1.7) — 재생목록 안의
     *                     조각 주소에 서명을 박아 내려야 하기 때문이다
     * @param thumbnailUrl 같은 프리픽스의 {@code thumb.jpg}. 단일 파일이라 리라이팅 없이
     *                     서명 URL 을 바로 낸다
     */
    @Schema(description = "대표 모티프")
    public record RepMotif(
            Long resultSeq,
            @Schema(description = "HLS 마스터 재생목록 (core-api 경로 — §1.7)",
                    example = "/api/v1/media/9c1d2e3f-4a5b-6c7d-8e9f-0a1b2c3d4e5f/master.m3u8")
            String videoUrl,
            @Schema(description = "CloudFront signed URL")
            String thumbnailUrl) {
    }

}
