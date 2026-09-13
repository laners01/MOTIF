package com.ssafy.motif.coreapi.domain.challenge.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 숏폼 피드 항목 (API 명세 §7.1).
 *
 * <p>§7.2 상세와 겹치는 칸이 많지만 <b>합치지 않는다</b> — 피드에는 `description`·슬롯이
 * 없고(스와이프 중에 쓰지 않는다), 상세에는 커서가 없다. 한 record 로 묶으면 피드
 * 항목마다 슬롯을 읽게 되고 그게 한 페이지에 20번의 추가 조회가 된다.
 *
 * <p><b>{@code title} 은 없다</b> (회의 확정 R3 — `challenges.title` 컬럼 삭제).
 * 화면 식별은 곡명({@code song.title})·아티스트·썸네일로 한다.
 *
 * @param thumbnailUrl 첫 프레임 로딩 전 플레이스홀더 (회의 확정 R2)
 * @param participantCount 게시 참여 수 — <b>PUBLIC·PRIVATE 를 모두 포함</b>한다.
 *                         PRIVATE 영상의 조회 비노출과 카운트 집계는 분리한다
 * @param isSubscribed 피드 오버레이의 구독 버튼용 — 이 칸이 §7.1 소속인 이유다
 */
@Schema(description = "숏폼 피드 항목 (§7.1)")
public record ChallengeFeedItem(
        Integer challengeSeq,

        @Schema(description = "HLS 마스터 재생목록. 후처리 전이면 원본 MP4 다")
        String videoUrl,

        String thumbnailUrl,
        int durationMs,
        Artist artist,
        Song song,
        long viewCount,
        int participantCount,
        int likeCount,
        int commentCount,
        boolean isLiked,
        boolean isSubscribed
) {

    public record Artist(Integer artistSeq, String name, String profileImageUrl) {
    }

    /** {@code groupName} 은 없다 (회의 확정). */
    public record Song(String title, String singerName) {
    }
}
