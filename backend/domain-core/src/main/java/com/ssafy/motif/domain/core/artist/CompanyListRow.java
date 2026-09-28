package com.ssafy.motif.domain.core.artist;

import java.time.Instant;

/**
 * 소속사 목록 한 줄 (어드민 명세 §3.3.1).
 *
 * <p>{@code groupCount}·{@code artistCount} 는 <b>비삭제</b> 참조 수다 — 삭제 차단
 * (§3.3.4 {@code COMPANY_IN_USE})이 보는 것과 같은 수라, 화면이 "삭제하면 막힐지"를
 * 미리 알 수 있다.
 */
public record CompanyListRow(
        Integer companySeq, String companyName,
        long groupCount, long artistCount,
        boolean deleted, Instant regDttm
) {
}
