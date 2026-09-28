package com.ssafy.motif.coreapi.domain.artist.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 구독 / 구독 취소 결과 (API 명세 §5.6 · §5.7).
 *
 * <p><b>좋아요와 달리 204 가 아니다.</b> §1.2 의 "성공만 204" 예외는 좋아요 토글 전용이고,
 * 구독은 클라이언트가 낙관적으로 반전해 둔 UI 를 <b>보정</b>할 값을 돌려받아야 한다 —
 * 연타 규약(§5.6)이 "응답의 {@code isSubscribed}·{@code subscriberCount} 로 UI 를
 * 보정한다"로 정해져 있다.
 *
 * @param subscriberCount 증감이 반영된 <b>현재</b> 값. 멱등 호출(이미 구독 중 POST ·
 *                        미구독 DELETE)에서는 변하지 않은 값이 그대로 나간다
 */
@Schema(description = "구독 상태 (§5.6 · §5.7)")
public record SubscriptionResponse(

        @Schema(description = "이 호출 뒤의 구독 여부", example = "true")
        boolean isSubscribed,

        @Schema(description = "증감이 반영된 현재 구독자 수", example = "15231")
        int subscriberCount
) {
}
