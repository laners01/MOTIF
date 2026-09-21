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
 * <p><b>{@code availableMasks} 도 없다</b>(회의 확정 R4). 고를 수 있는 슬롯인지는 항목마다
 * {@code participants[].selectable} 로 내려가고, 그래도 없는 슬롯을 보내면 §13.1 의
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
        String description,

        @Schema(description = "교체 가능한 인물 슬롯. slotOrder 오름차순 = 화면 좌→우")
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
     * <p><b>편집본 정보가 여기 들어왔다</b>(0019). 예전에는 조합별 {@code editedChallenges[]}
     * 배열이 따로 있었지만, 편집본이 슬롯 1개당 1개가 되면서 배열 하나로 합쳐졌다 —
     * 사용자는 <b>함께할 아티스트 한 명</b>을 고르고, 그 슬롯의 마스크 하나를 §13.1 에 보낸다.
     *
     * @param participantMask <b>단일 비트</b>({@code 1, 2, 4, 8}) = {@code 1 << slotOrder}.
     *                        §13.1 의 {@code participantMask} 에 그대로 싣는다
     */
    public record Participant(
            Integer artistSeq,

            @Schema(description = "아티스트 표시 이름 (users.nickname)", example = "윈터")
            String name,

            @Schema(description = "화면 좌→우 순서, 0부터", example = "0")
            int slotOrder,

            @Schema(description = "단일 비트 마스크 (1·2·4·8) = 1 << slotOrder", example = "1")
            int participantMask,

            @Schema(description = "프로필 이미지 URL. 미등록이면 null")
            String profileImageUrl,

            @Schema(description = "선택 화면에서 프로필 원이 앉는 자리. 어드민이 아직 안 찍은 옛 슬롯은 null — 원을 숨긴다",
                    nullable = true)
            Anchor anchor,

            @Schema(description = """
                    이 슬롯을 고를 수 있는가. 편집본(그 사람을 지운 영상)이 아직 없으면 false 이고,
                    그래도 §13.1 로 보내면 EDITED_CHALLENGE_NOT_FOUND 다 — 화면에서 미리 잠근다""")
            boolean selectable,

            @Schema(description = "이 슬롯을 골랐을 때 썸네일 자리에 보이는 이미지. 없으면 null — thumbnailUrl 로 폴백한다",
                    nullable = true)
            String previewImageUrl
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
