package com.ssafy.motif.coreapi.admin.banner.dto;

import java.time.Instant;

/**
 * 배너 목록 항목 (어드민 명세 §3.11.1).
 *
 * @param imageUrl 공개 CDN URL. base URL 미설정(로컬·CI)이면 null 이라 화면이 키를 대신 보여준다
 * @param imageKey 화면이 교체 전후를 눈으로 대조할 수 있게 키도 같이 준다
 */
public record BannerItem(
        Integer shopBannerSeq,
        String imageUrl,
        String imageKey,
        short displayOrder,
        boolean isActive,
        Instant regDttm) {
}
