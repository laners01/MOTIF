package com.ssafy.motif.coreapi.domain.home.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 홈 HOT 참여 영상 카드 (API 명세 §6.1).
 *
 * <p><b>경량 항목이다</b>(회의 확정 R2 — §8.2 전체에서 축소). 좋아요·댓글 수와
 * {@code isLiked} 같은 오버레이 정보는 담지 않는다 — 뷰어에 들어갈 때 §8.2 단건 조회
 * 1회로 받는다. 클릭 즉시 재생을 시작할 수 있는 최소 정보만 싣는 것이 목적이다.
 *
 * <p>홈 가로 캐러셀과 세로 뷰어가 <b>같은 항목</b>을 쓴다(R4 — 뷰어용 API 가 따로
 * 없다).
 *
 * @param rank      스냅샷 순위. 커서로 이어 받아도 이어지는 번호이며, 사이에 비노출로
 *                  빠진 영상이 있으면 <b>건너뛴 번호가 생긴다</b>(§1.8). 다시 매기면 같은
 *                  커서로 받은 다음 페이지와 어긋난다
 * @param rankDelta 직전 순위 − 현재 순위 — 상승이 양수다. <b>신규 진입은
 *                  {@code null}</b>(§5.1 과 같은 규약). 대비 구간은 1시간이고, 값은 배치가
 *                  적재 시점에 계산해 둔 것이다(ADR-0015)
 * @param author    작성자 요약. {@code profileImageUrl} 은 카드에 쓰지 않아 싣지 않는다
 */
@Schema(description = "HOT 참여 영상 카드 (§6.1)")
public record HotVideoItem(
        @Schema(description = "스냅샷 순위(1-base)")
        int rank,

        @Schema(description = "직전 순위 − 현재 순위. 상승이 양수, 신규 진입은 null", nullable = true)
        Integer rankDelta,

        Long videoSeq,

        @Schema(description = "HLS 마스터 재생목록. 참여 영상은 전부 보호 등급이라 프리픽스 서명이 붙는다")
        String videoUrl,

        String thumbnailUrl,
        int durationMs,
        Song song,
        Author author,
        long viewCount
) {

    /** 카드 하단 표기용 — 화면에는 "mochico · bailar toda la noche" 로 나온다. */
    public record Song(String title, String singerName) {
    }

    public record Author(Integer userSeq, String nickname) {
    }
}
