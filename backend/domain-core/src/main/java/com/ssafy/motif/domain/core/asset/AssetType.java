package com.ssafy.motif.domain.core.asset;

/**
 * 에셋 종류 (DB SPEC v0.15 §30 — {@code ck_motif_assets_type}).
 *
 * <p>노출·구매·PASS 커버 판정에서 <b>{@code SECRET} 은 {@code NORMAL} 과 같게</b> 다루고,
 * {@code PREMIUM} 만 개별 구매 전용이다(PASS 미커버, 회의 확정 R5). 판정 코드는 구매
 * 도메인 소유라 여기서는 값만 고정한다.
 */
public enum AssetType {
    NORMAL, PREMIUM, SECRET
}
