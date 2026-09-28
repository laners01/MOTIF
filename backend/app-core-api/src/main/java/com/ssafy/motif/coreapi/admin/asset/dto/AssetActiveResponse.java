package com.ssafy.motif.coreapi.admin.asset.dto;

/**
 * 활성 전환 결과 (어드민 명세 §3.8.5).
 *
 * @param inactivePriceCount 비활성으로 남은 가격 행 수 — <b>경고용</b>이다. 재활성화는
 *                           가격을 자동으로 되살리지 않으므로(DB §30), 이 값이 0 이 아닌
 *                           채로 활성화하면 그 국가는 팔리지 않는다. 화면이 이 숫자로
 *                           "가격을 다시 켜라"를 띄운다
 */
public record AssetActiveResponse(Integer assetSeq, boolean isActive, long inactivePriceCount) {
}
