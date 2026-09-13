package com.ssafy.motif.coreapi.domain.challenge.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 명세 §7.2 성공 응답의 {@code data} — 챌린지 상세 Bottom Sheet.
 *
 * <p>§7.1 피드 항목 + {@code description} + 슬롯 정보다.
 *
 * <p><b>{@code title} 이 없다</b> — R3 에서 {@code challenges.title} 컬럼이 삭제됐고,
 * 화면 식별은 곡명({@code song.title})과 아티스트로 한다.
 *
 * <p><b>{@code availableMasks} 도 없다</b>(회의 확정 R4). 슬롯 조합의 편집본은 어드민이
 * 전부 등록한다는 운영 전제이고, 조합 유효성은 §13.1 의 {@code EDITED_CHALLENGE_NOT_FOUND}
 * 로만 사후 판별된다.
 *
 * @param participantCount 게시 참여 수 — <b>PUBLIC·PRIVATE 를 모두 포함</b>한다.
 *                         PRIVATE 영상의 조회 비노출(§1.8)과 카운트 집계는 분리된 규칙이다
 */
public record ChallengeDetailResponse(
        Integer challengeSeq,

        @Schema(description = "마스터 재생목록(master.m3u8) — 챌린지 원본은 HLS·공개 등급이다 (§1.7, ADR-0010)")
        String videoUrl,

        @Schema(description = "첫 프레임 로딩 전 플레이스홀더. CloudFront 일반 URL", nullable = true)
        String thumbnailUrl,

        int durationMs,

        Artist artist,

        Song song,

        long viewCount,

        @Schema(description = "게시 참여 수 (PUBLIC·PRIVATE 포함)")
        int participantCount,

        int likeCount,

        int commentCount,

        @Schema(description = "내가 이 챌린지에 좋아요를 눌렀는지")
        boolean isLiked,

        @Schema(description = "내가 이 아티스트를 구독 중인지 (오버레이 구독 버튼용)")
        boolean isSubscribed,

        @Schema(description = "챌린지 소개글")
        String description,

        @Schema(description = "교체 가능한 인물 슬롯. 마스크 오름차순 = 화면 좌→우")
        List<Participant> participants
) {

    /** §7.1·§7.2 {@code artist} — {@code groupName} 은 제거됐다(회의 확정). */
    public record Artist(
            Integer artistSeq,

            @Schema(description = "아티스트 표시 이름 (users.nickname)", example = "카리나")
            String name,

            @Schema(description = "CloudFront 일반 URL(공개 등급 — §1.7). 미설정이면 null", nullable = true)
            String profileImageUrl
    ) {
    }

    /** §7.1·§7.2 {@code song} — 화면 식별의 주된 근거다({@code title} 이 없으므로). */
    public record Song(
            @Schema(example = "Supernova")
            String title,

            @Schema(example = "aespa")
            String singerName
    ) {
    }

    /**
     * §7.2 {@code participants[]} — 교체 가능한 추가 참여 아티스트 슬롯.
     *
     * <p>{@code groupName} 은 제거됐다(회의 확정).
     *
     * @param participantMask <b>단일 비트</b>({@code 1, 2, 4, 8}). 서버가 화면 좌→우
     *                        순서대로 {@code 1 << index} 로 부여한 값이다
     */
    public record Participant(
            Integer artistSeq,

            @Schema(description = "아티스트 표시 이름 (users.nickname)", example = "윈터")
            String name,

            @Schema(description = "단일 비트 마스크 (1·2·4·8)", example = "1")
            int participantMask,

            @Schema(description = "프로필 이미지 URL. 미등록이면 null")
            String profileImageUrl,

            Bbox bbox
    ) {
    }

    /** 원본 영상 px · 좌상단 원점 — 촬영 가이드 오버레이용이다(§7.2). */
    public record Bbox(
            int leftTopX,
            int leftTopY,
            int rightBottomX,
            int rightBottomY
    ) {
    }
}
