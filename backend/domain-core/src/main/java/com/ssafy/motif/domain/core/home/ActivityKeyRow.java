package com.ssafy.motif.domain.core.home;

import java.time.Instant;

/**
 * 활동 피드 한 자리의 <b>식별 정보만</b> (API 명세 §6.3) —
 * {@link HomeActivityRepository#findActivities} 투영.
 *
 * <p>인터페이스 투영인 것은 네이티브 쿼리라서다({@code MotifAssetRepository.AssetListRow}
 * 와 같은 방식). 생성자 투영은 JPQL 에서만 쓸 수 있다.
 *
 * @see HomeActivityRepository#findActivities 왜 본문을 함께 읽지 않는지
 */
public interface ActivityKeyRow {

    /** {@code MOTIF_ASSET} | {@code NOTICE}. */
    String getType();

    /** 타입에 따라 {@code assetSeq} 또는 {@code noticeSeq} 다 — 둘 다 INTEGER. */
    Integer getSeq();

    Instant getRegDttm();
}
