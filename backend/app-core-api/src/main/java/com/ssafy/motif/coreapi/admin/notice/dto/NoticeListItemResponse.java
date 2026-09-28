package com.ssafy.motif.coreapi.admin.notice.dto;

import java.time.Instant;

/**
 * 공지 목록 항목 (어드민 명세 §3.10.1).
 *
 * <p>본문 전체가 아니라 앞자락만 싣는다 — 목록 한 페이지가 100건이고 {@code content} 는
 * {@code TEXT} 다. 전체 본문은 상세({@link NoticeDetailResponse})에서만 준다.
 *
 * @param contentPreview 본문 앞 80자. 이벤트 페이로드의 60자(§4)와는 다른 값이다
 * @param isDeleted      목록은 삭제분을 <b>지우지 않고 회색으로</b> 보여 준다 —
 *                       화면(572)이 이 값으로 편집·삭제 버튼을 뺀다
 */
public record NoticeListItemResponse(
        Integer noticeSeq,
        ArtistSummary artist,
        String contentPreview,
        int imageCount,
        int likeCount,
        int commentCount,
        boolean isDeleted,
        Instant regDttm
) {

    /** 목록에 필요한 아티스트 정보는 이름 하나뿐이다 — {@code users.nickname}. */
    public record ArtistSummary(Integer artistSeq, String name) {
    }
}
