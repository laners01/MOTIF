package com.ssafy.motif.coreapi.domain.asset.dto;

import com.ssafy.motif.domain.core.asset.AssetType;

/**
 * 에셋 하나에 대한 내 이용 권한 (API 명세 §11.1 · §11.2).
 *
 * <p><b>우선순위는 {@code OWNED > PASS > NONE}</b> 이다. 개별 구매와 패스가 동시에
 * 유효하면 {@code OWNED} 다 — 패스가 끝나도 남는 권한이 더 강한 사실이라 그쪽을 답한다.
 *
 * <p>판정을 {@link #of} 하나로 모으는 이유는 §11.1 목록과 §11.2 상세가 <b>같은 답</b>을
 * 줘야 하기 때문이다. 목록에서 {@code PASS} 로 열려 보이는데 상세에서 {@code NONE} 이면
 * 구매 버튼이 화면마다 달라진다.
 */
public enum MyAccess {

    /** 개별 구매 보유 ({@code asset_purchases.status='ACTIVE'}). */
    OWNED,

    /** 그 아티스트의 유효 패스로 열린다. */
    PASS,

    /** 권한 없음. */
    NONE;

    /**
     * 구매·패스 보유 여부와 에셋 타입으로 권한을 정한다.
     *
     * <p><b>{@code PREMIUM} 은 패스로 열리지 않는다</b>(회의 확정 R5) — 유효 패스가 있어도
     * {@code NONE} 이다. 반대로 {@code SECRET} 은 {@code NORMAL} 과 완전히 같아 패스가
     * 커버한다. 이 분기를 SQL 이 아니라 여기 두는 것은 DB 없이 고정할 수 있는 규칙이기
     * 때문이다.
     *
     * @param passActive 그 아티스트의 유효 패스 보유 여부 — <b>에셋 타입을 보지 않은 값</b>
     */
    public static MyAccess of(boolean owned, boolean passActive, AssetType assetType) {
        if (owned) {
            return OWNED;
        }
        if (passActive && assetType != AssetType.PREMIUM) {
            return PASS;
        }
        return NONE;
    }
}
