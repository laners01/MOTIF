package com.ssafy.motif.coreapi.admin.notice.dto;

import java.time.Instant;
import java.util.List;

/**
 * 공지 상세 (어드민 명세 §3.10.3) — §3.10.4 수정 응답도 이 모양이다.
 *
 * <p>수정 화면(572)이 저장 직후 다시 조회하지 않아도 되도록 교체 결과를 그대로 돌려준다.
 */
public record NoticeDetailResponse(
        Integer noticeSeq, Integer artistSeq, String content,
        List<NoticeImageResponse> images,
        int likeCount, int commentCount, boolean isDeleted,
        Instant regDttm, Instant modDttm
) {
}
