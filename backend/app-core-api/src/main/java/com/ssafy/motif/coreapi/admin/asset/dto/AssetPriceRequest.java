package com.ssafy.motif.coreapi.admin.asset.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

/**
 * 국가 가격 upsert 요청 (어드민 명세 §3.8.6) · 등록 시 KR 가격(§3.8.2).
 *
 * @param amount   {@code NUMERIC(10,2)} 에 대응하는 {@link BigDecimal} — {@code "3900.00"}
 *                 문자열로 온다. <b>부동소수를 쓰지 않는다</b>(DB §31)
 * @param currency ISO 4217 형식. 실제 유효 코드 검증은 형식 CHECK 와 같은 범위로 둔다 —
 *                 현재 쓰는 값은 {@code KRW} 하나이고, 화이트리스트는 국가가 늘 때 넣는다
 * @param isActive 등록 요청에서는 무시하고 항상 활성으로 만든다(§3.8.2 — 판매 가능 상태)
 */
public record AssetPriceRequest(
        @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 8, fraction = 2)
        BigDecimal amount,
        @NotNull @Pattern(regexp = "^[A-Z]{3}$") String currency,
        Boolean isActive
) {
}
