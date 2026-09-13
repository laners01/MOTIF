package com.ssafy.motif.coreapi.admin.pass.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

/**
 * 패스 가격 upsert 요청 (어드민 명세 §3.9.2).
 *
 * <p><b>소급 적용이 없다.</b> 여기서 바꾼 값은 <b>갱신 이후 주문</b>부터 쓰이고, 이미
 * 취득한 ACTIVE PASS 의 금액·만료는 그대로다 — 결제 시점 금액은 {@code orders} 스냅샷이
 * 갖는다(DB §32). 자동 갱신 결제도 갱신 시점에 이 표를 다시 읽는다.
 *
 * @param amount   {@code NUMERIC(10,2)} 대응 {@link BigDecimal} — 부동소수를 쓰지 않는다
 * @param isActive 생략하면 활성으로 본다
 */
public record PassPriceRequest(
        @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 8, fraction = 2)
        BigDecimal amount,
        @NotNull @Pattern(regexp = "^[A-Z]{3}$") String currency,
        Boolean isActive
) {
}
