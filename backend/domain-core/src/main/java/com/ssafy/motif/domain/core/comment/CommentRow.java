package com.ssafy.motif.domain.core.comment;

import java.time.Instant;

/**
 * 댓글 목록 한 줄 (API 명세 §9.1) — 세 리포지토리의 루트 목록 쿼리가 공통으로 쓰는 투영.
 *
 * <p><b>대상 3종이 같은 투영을 쓰는 것은 계약이 하나이기 때문이다.</b> 명세 §9 가 챌린지·
 * 참여 영상·공지 댓글을 <b>하나의 응답 형태</b>로 못박았다 — 지금 우연히 칸이 겹치는
 * 것이 아니라 같은 것을 담기로 정해져 있다. (칸이 같아 보인다고 합치면 안 되는 쪽은
 * <b>다른 API 끼리</b>다 — §5.1 랭킹이 §4.6 구독 투영을 가져다 쓰다 되돌린 사례가 있다.)
 *
 * <p><b>{@code isLiked}·{@code replyCount} 가 없다.</b> 둘은 이 쿼리로 읽지 않는다 —
 * 항목마다 물으면 한 페이지(최대 100건)에 왕복이 200번이다. 페이지의 seq 를 통째로 넘겨
 * 한 번에 받아 호출부가 대조한다.
 *
 * <p>프로필은 <b>키</b>다 — URL 로 바꾸는 것은 등급 판정이 있는 core-api 쪽
 * ({@code MediaUrlResolver}) 이고 domain-core 는 CDN 을 모른다.
 */
public record CommentRow(
        Long commentSeq,
        String content,
        Integer authorUserSeq,
        String authorNickname,
        String authorProfileImageKey,
        int likeCount,
        Instant regDttm
) {
}
