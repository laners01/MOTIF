package com.ssafy.motif.coreapi.admin.challenge.dto;

import com.ssafy.motif.coreapi.admin.challenge.dto.ChallengeDetailResponse.ArtistSummary;
import com.ssafy.motif.coreapi.admin.challenge.dto.ChallengeDetailResponse.SongSummary;
import com.ssafy.motif.domain.core.challenge.ChallengeListRow;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * 챌린지 목록 항목 (어드민 명세 §3.7.1).
 *
 * <p>상세(§3.7.3)는 이 항목에 슬롯·편집본 매트릭스를 더한 것이다 — 그래서 요약
 * 레코드({@link ArtistSummary}·{@link SongSummary})를 {@link ChallengeDetailResponse}
 * 것으로 그대로 쓴다. 같은 모양을 두 벌 두면 한쪽만 고쳐지고 화면이 둘을 다르게
 * 파싱하게 된다.
 */
@Schema(description = "챌린지 목록 항목")
public record ChallengeListItem(
        Integer challengeSeq,
        ArtistSummary artist,
        SongSummary song,
        String thumbnailUrl,
        int durationMs,
        long slotCount,
        long editedCount,
        long viewCount,
        int participantCount,
        int likeCount,
        int commentCount,
        boolean isDeleted,
        Instant regDttm) {

    public static ChallengeListItem of(ChallengeListRow row, String thumbnailUrl) {
        return new ChallengeListItem(
                row.challengeSeq(),
                new ArtistSummary(row.artistSeq(), row.artistName()),
                new SongSummary(row.songSeq(), row.songTitle(), row.singerName()),
                thumbnailUrl,
                row.durationMs(),
                row.slotCount(),
                row.editedCount(),
                row.viewCount(),
                row.participantCount(),
                row.likeCount(),
                row.commentCount(),
                row.deleted(),
                row.regDttm());
    }
}
