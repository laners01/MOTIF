package com.ssafy.motif.coreapi.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * 구독 아티스트 카드 (API 명세 §4.7).
 *
 * <p><b>카드 앞면·뒷면을 한 번에 담는다</b> — 뒤집을 때 다시 부르지 않는다.
 * 마이페이지뿐 아니라 <b>숍 화면의 아티스트 섹션</b>도 이 응답으로 구성한다
 * (구 §11.3 집계 API 는 삭제됐다). 아티스트별 에셋은 §11.1 의 {@code artistSeq}
 * 필터로 따로 조회한다.
 *
 * <p>앞의 네 칸은 <b>공통 아티스트 요약</b>(상세 명세 README §3.5)이고 나머지가 집계다.
 * 요약을 중첩 객체로 두지 않은 것은 명세 예시가 평탄화된 형태이기 때문이다 — §4.6 이
 * 중첩으로 쓰는 유일한 자리라 아직 공용 record 를 꺼내지 않았다.
 *
 * @param groupName           솔로 아티스트는 {@code null}
 * @param passActive          유효 패스 보유 여부. 거짓이면 클라이언트가 "모티프 패스
 *                            알아보기"(→ §11.4)를 노출한다
 * @param passExpiresDttm     {@code passActive} 가 참일 때의 만료 시각. 아니면 {@code null}
 * @param subscribedDays      구독 경과일
 * @param challengeCount      그 아티스트에 올린 <b>내 참여 영상 수</b>. 이름과 달리
 *                            챌린지를 세지 않는다(명세 §4.7 표)
 * @param purchasedAssetCount <b>개별 구매한</b> 에셋 수. 패스로 열린 것은 빠진다 —
 *                            패스만 있으면 {@code 0/m} 이다
 * @param totalAssetCount     판매 중({@code is_active=TRUE})인 에셋 수. {@code SECRET} 포함
 */
@Schema(description = "구독 아티스트 카드 (§4.7)")
public record SubscriptionCardItem(
        Integer artistSeq,
        String name,
        @Schema(description = "솔로 아티스트는 null", nullable = true)
        String groupName,
        @Schema(description = "공개 CDN URL. 미설정이면 기본 프로필 이미지")
        String profileImageUrl,
        boolean passActive,
        @Schema(description = "패스가 없으면 null", nullable = true)
        Instant passExpiresDttm,
        long subscribedDays,
        @Schema(description = "그 아티스트에 올린 내 참여 영상 수")
        long challengeCount,
        @Schema(description = "개별 구매한 에셋 수. 패스로 열린 것은 제외 — 카드의 n/m 중 n")
        long purchasedAssetCount,
        @Schema(description = "판매 중인 에셋 수(SECRET 포함) — 카드의 n/m 중 m")
        long totalAssetCount) {
}
