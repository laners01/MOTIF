package com.ssafy.motif.coreapi.domain.challenge.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 명세 §7.2 성공 응답의 {@code data} — 챌린지 상세 Bottom Sheet.
 *
 * <p>§7.1 피드 항목과 <b>같은 칸</b>이다 — {@code description} 이 §7.1 에도 실리면서
 * 상세만 갖는 칸이 없어졌다. 그래도 record 는 따로 둔다({@code ChallengeFeedItem} 주석).
 *
 * <p><b>{@code title} 이 없다</b> — R3 에서 {@code challenges.title} 컬럼이 삭제됐고,
 * 화면 식별은 곡명({@code song.title})과 아티스트로 한다.
 *
 * <p><b>{@code participants[]} 가 §7.5 로 떨어져 나갔다</b>(0020). 촬영 선택 화면이 쓰는
 * 데이터라 이 시트와 수명이 다르고, 편집본 이미지에 서명 만료가 붙으면서 상세를 열어 둔
 * 채 오래 머물면 죽는 값이 됐다. 참여자 아바타가 필요한 화면은 §7.5 를 부른다.
 *
 * <p><b>{@code availableMasks} 도 없다</b>(회의 확정 R4). 고를 수 있는 슬롯인지는 §7.5 의
 * {@code initialSceneUrl} 유무가 답하고, 그래도 없는 슬롯을 보내면 §13.1 의
 * {@code EDITED_CHALLENGE_NOT_FOUND} 가 사후 판별한다.
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
        String description
) {

    /** §7.1·§7.2 {@code artist} — {@code groupName} 은 제거됐다(회의 확정). */
    public record Artist(
            Integer artistSeq,

            @Schema(description = "아티스트 표시 이름 (users.nickname)", example = "카리나")
            String name,

            @Schema(description = "CloudFront 일반 URL(공개 등급 — §1.7). 미설정이면 기본 프로필 이미지")
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
     * 썸네일 대비 <b>0~1 비율</b> — {@code x·r} 은 가로, {@code y} 는 세로 기준. 렌더 크기를 곱해
     * px 로 만든다: 중심 {@code (x·W, y·H)}, 반지름 {@code r·W}. 시안(402 폭, 지름 80)이면
     * {@code r ≈ 0.0995}.
     */
    public record Anchor(
            @Schema(example = "0.22") double x,
            @Schema(example = "0.36") double y,
            @Schema(example = "0.0995") double r
    ) {
    }

}
