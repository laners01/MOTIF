package com.ssafy.motif.coreapi.admin.challenge.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/**
 * 챌린지 상세 (어드민 명세 §3.7.3) — §3.7.1 목록 항목 + 슬롯·편집본 매트릭스.
 *
 * @param editedChallenges 각 편집본의 {@code jobRefCount} 가 0 이 아니면 합성 이력이 있다는 뜻이고,
 *                         그 챌린지의 슬롯 구성은 사실상 고정이다(§3.7.5 {@code EDITED_IN_USE}).
 *                         화면은 이 값으로 "슬롯 교체" 버튼 활성 여부를 정한다
 * @param activeJobCount   QUEUED·PROCESSING 잡 수 — 진행 중인 참여가 있는지
 */
@Schema(description = "챌린지 상세")
public record ChallengeDetailResponse(
        Integer challengeSeq,
        ArtistSummary artist,
        SongSummary song,
        String description,
        String videoUrl,
        String thumbnailUrl,
        int durationMs,
        int slotCount,
        int editedCount,
        long viewCount,
        int participantCount,
        int likeCount,
        int commentCount,
        boolean isDeleted,
        Instant regDttm,
        List<Participant> participants,
        List<Edited> editedChallenges,
        long activeJobCount) {

    public record ArtistSummary(Integer artistSeq, String name) {
    }

    public record SongSummary(Integer songSeq, String title, String singerName) {
    }

    public record Participant(ArtistSummary artist, int participantMask, BboxResponse bbox) {
    }

    public record BboxResponse(int leftTopX, int leftTopY, int rightBottomX, int rightBottomY) {
    }

    public record Edited(Integer editedChallengeSeq, int participantMask,
                         String videoUrl, String initialSceneUrl, long jobRefCount) {
    }
}
