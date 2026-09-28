package com.ssafy.motif.coreapi.admin.artist.dto;

/**
 * 아티스트 삭제 연쇄 결과 (어드민 명세 §3.5.5, 200).
 *
 * <p>세 숫자는 <b>이번 삭제로 실제 UPDATE 된 행 수</b>다 — 아티스트가 가진 총 개수가
 * 아니다. 이미 꺼져 있던 에셋·가격은 세지 않는다. 화면이 이 값을 그대로 보여 주므로
 * 추정값을 넣으면 운영자가 "에셋도 같이 꺼졌다" 를 잘못 읽는다.
 */
public record ArtistDeleteResponse(int deactivatedAssets, int deactivatedAssetPrices,
                                   int deactivatedPassPrices) {
}
