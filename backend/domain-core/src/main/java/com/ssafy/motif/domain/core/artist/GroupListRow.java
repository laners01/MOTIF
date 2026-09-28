package com.ssafy.motif.domain.core.artist;

import java.time.Instant;

/**
 * 그룹 목록 한 줄 (어드민 명세 §3.4.1).
 *
 * <p>소속사는 FK NOT NULL 이라 inner join 이다 — 그룹에 소속사가 없는 상태는 없다.
 * {@code memberCount} 는 비삭제 멤버 수로, §3.4.4 {@code GROUP_IN_USE} 가 보는 수와 같다.
 */
public record GroupListRow(
        Integer groupSeq, String groupName,
        Integer companySeq, String companyName,
        long memberCount,
        boolean deleted, Instant regDttm
) {
}
