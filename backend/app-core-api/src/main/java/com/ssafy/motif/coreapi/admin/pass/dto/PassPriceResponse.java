package com.ssafy.motif.coreapi.admin.pass.dto;

import com.ssafy.motif.domain.core.pass.MotifPassPrice;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 패스 가격 항목 (어드민 명세 §3.9.1 목록 · §3.9.2 응답).
 *
 * <p>목록은 <b>설정된 행만</b> 준다 — 화면이 국가×개월 그리드로 펼치고 없는 칸을
 * "미설정"으로 비운다(§3.9.1 "빈 칸 = 미설정"). 서버가 빈 조합을 만들어 보내면
 * 기간 후보를 서버가 정하는 셈이 되는데, DB 는 개월 수를 고정하지 않는다(DB §32).
 *
 * @param activePassCount 이 (아티스트, 개월) 의 유효 PASS 수 — <b>경고용</b>이다.
 *                        값이 있어도 비활성화를 막지 않는다(운영 판단, S15P21D104-569)
 */
public record PassPriceResponse(
        Integer passPriceSeq, String countryCode, short durationMonths,
        BigDecimal amount, String currency, boolean isActive,
        long activePassCount, Instant modDttm
) {

    public static PassPriceResponse of(MotifPassPrice price, String countryCode, long activePassCount) {
        return new PassPriceResponse(price.getPassPriceSeq(), countryCode, price.getDurationMonths(),
                price.getAmount(), price.getCurrency(), price.isActive(),
                activePassCount, price.getModDttm());
    }
}
