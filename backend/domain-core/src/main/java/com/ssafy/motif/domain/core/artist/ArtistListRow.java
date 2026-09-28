package com.ssafy.motif.domain.core.artist;

import java.time.Instant;

/**
 * 아티스트 목록 한 줄 (어드민 명세 §3.5.1) — {@link ArtistRepository#search} 투영.
 *
 * <p>이름·프로필은 {@code users}, 그룹명·소속사명은 각 마스터 테이블에 있다. 엔티티에
 * {@code @ManyToOne} 을 달면 목록이 아닌 조회까지 조인을 끌고 다니므로, 필요한 칸만
 * 조인해 꺼낸다({@link ArtistName} 과 같은 근거).
 *
 * <p>{@code groupSeq}·{@code groupName} 은 솔로면 {@code null} 이다(left join).
 * 카운터 3종은 여기 없다 — 행마다 세면 N+1 이라 서비스가 한 번에 묶어 붙인다.
 */
public record ArtistListRow(
        Integer artistSeq, Integer userSeq, String name, String profileImageKey,
        Integer groupSeq, String groupName, Integer companySeq, String companyName,
        String bio, String motifBgImageKey,
        int subscriberCount, boolean deleted, Instant regDttm
) {
}
