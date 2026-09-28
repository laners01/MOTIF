package com.ssafy.motif.coreapi.admin.challenge.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/**
 * 챌린지 상세 (어드민 명세 §3.7.3) — §3.7.1 목록 항목 + 슬롯 표.
 *
 * <p><b>{@code editedChallenges[]} 가 없어졌다</b>(0019). 편집본이 슬롯 1개당 1개가 되면서
 * {@link Participant} 안으로 들어갔다 — 화면의 "슬롯 × 편집본" 매트릭스도 슬롯 한 줄이 된다.
 *
 * @param editedCount    편집본이 올라온 슬롯 수 — {@code slotCount} 와 같으면 전부 채워졌다
 * @param activeJobCount QUEUED·PROCESSING 잡 수 — 진행 중인 참여가 있는지
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
        long activeJobCount) {

    /** @param profileImageUrl 상세 화면이 슬롯 원 안에 넣는다 — 미등록이면 {@code null} */
    public record ArtistSummary(Integer artistSeq, String name, String profileImageUrl) {
    }

    public record SongSummary(Integer songSeq, String title, String singerName) {
    }

    /**
     * 슬롯 1개 — 인물·자리·편집본이 한 줄이다.
     *
     * <p>키도 URL 과 함께 준다 — 슬롯 교체(§3.7.5)가 편집본을 <b>다시 보내야</b> 하는데,
     * URL 에서 키를 역산하면 CDN 주소 형태가 바뀔 때 조용히 깨진다.
     *
     * @param anchor       0017 이전 행은 {@code null} — 화면이 "위치 지정" 을 재촉한다(§3.7.9)
     * @param jobRefCount  0 이 아니면 이 슬롯으로 합성한 이력이 있다는 뜻이고, 그 챌린지의
     *                     슬롯 구성은 사실상 고정이다(§3.7.5 {@code EDITED_IN_USE}). 화면은 이
     *                     값으로 "슬롯 교체" 버튼 활성 여부를 정한다
     */
    public record Participant(Integer challengeParticipantSeq,
                              ArtistSummary artist,
                              int slotOrder,
                              AnchorResponse anchor,
                              String editedChallengeUrl, String initialSceneUrl, String previewImageUrl,
                              String editedChallengeKey, String initialSceneKey, String previewImageKey,
                              Integer editedDurationMs,
                              long jobRefCount) {
    }

    public record AnchorResponse(double x, double y, double r) {
    }
}
