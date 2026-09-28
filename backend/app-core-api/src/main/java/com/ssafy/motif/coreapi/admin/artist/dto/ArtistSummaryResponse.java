package com.ssafy.motif.coreapi.admin.artist.dto;

import java.time.Instant;

/**
 * 아티스트 목록 항목 (어드민 명세 §3.5.1).
 *
 * @param group   솔로면 {@code null}
 * @param counts  운영 지표 — 챌린지·에셋·공지 수. 페이지 단위로 한 번에 집계해 붙인다
 */
public record ArtistSummaryResponse(
        Integer artistSeq, Integer userSeq, String name,
        GroupRef group, CompanyRef company,
        String profileImageUrl,
        int subscriberCount, long challengeCount, long assetCount, long noticeCount,
        boolean isDeleted, Instant regDttm
) {

    public record GroupRef(Integer groupSeq, String groupName) {
    }

    public record CompanyRef(Integer companySeq, String companyName) {
    }
}
