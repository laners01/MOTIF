package com.ssafy.motif.coreapi.domain.artist.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/**
 * 명세 §5.3 성공 응답의 {@code data} — 아티스트 상세.
 *
 * @param name           {@code users.nickname} 이다 — {@code artists} 에 이름 칸이 없다
 * @param groupName      솔로면 {@code null}
 * @param subscribedDays 구독 경과일("WITH n days"). <b>미구독이면 {@code null}</b> (회의 확정 R2)
 * @param myMotifVideos  상단 자동재생용 최신 5개. 없으면 빈 배열(상단 영역 미노출)
 */
public record ArtistDetailResponse(
        Integer artistSeq,

        @Schema(description = "아티스트 표시 이름 (users.nickname)", example = "카리나")
        String name,

        @Schema(description = "소속 그룹명. 솔로면 null", nullable = true, example = "aespa")
        String groupName,

        @Schema(description = "CloudFront 일반 URL(공개 등급 — §1.7). 미설정이면 null", nullable = true)
        String profileImageUrl,

        @Schema(description = "소개글", nullable = true)
        String bio,

        @Schema(description = "구독자 수(거울 카운터)", example = "15230")
        int subscriberCount,

        @Schema(description = "이 아티스트의 비삭제 챌린지 수", example = "121")
        long challengeCount,

        @Schema(description = "내가 이 아티스트를 구독 중인지")
        boolean isSubscribed,

        @Schema(description = "구독 경과일. 미구독이면 null", nullable = true, example = "492")
        Long subscribedDays,

        MotifPass pass,

        @Schema(description = "내가 이 아티스트와 만든 모티프 결과물 최신 5개. 없으면 빈 배열")
        List<MyMotifVideo> myMotifVideos
) {

    /**
     * §5.3 {@code pass} — 모티프 패스 보유 여부.
     *
     * <p>{@code active=false} 면 클라이언트가 "모티프 패스 알아보기"(→ §11.4)를 노출한다.
     * 미보유면 {@code expiresDttm} 은 {@code null} 이다.
     */
    public record MotifPass(
            @Schema(description = "유효한 패스를 보유 중인지")
            boolean active,

            @Schema(description = "패스 만료 시각. 미보유면 null", nullable = true)
            Instant expiresDttm
    ) {

        private static final MotifPass INACTIVE = new MotifPass(false, null);

        public static MotifPass of(Instant expiresDttm) {
            return expiresDttm == null ? INACTIVE : new MotifPass(true, expiresDttm);
        }
    }

    /**
     * §5.3 {@code myMotifVideos[]} — 보관함 결과물 한 건.
     *
     * <p><b>보호 등급이다</b>(§1.7) — 이 두 URL 은 CloudFront signed URL 로만 접근하며(재생목록은 core-api 리라이팅)
     * {@code PublicMediaUrlResolver} 가 만드는 무기명 URL 을 쓰면 안 된다.
     */
    public record MyMotifVideo(
            Long resultSeq,

            @Schema(description = "master.m3u8 (HLS). core-api 가 서명해 다시 쓴 재생목록 — §1.7 보호 등급")
            String videoUrl,

            @Schema(description = "썸네일. signed URL — §1.7 보호 등급")
            String thumbnailUrl,

            int durationMs
    ) {
    }
}
