package com.ssafy.motif.coreapi.admin.asset.dto;

import com.ssafy.motif.domain.core.asset.MotifAssetPrice;

import java.math.BigDecimal;

/** 국가 가격 (어드민 명세 §3.8.1 {@code price} · §3.8.6 응답). */
public record AssetPriceResponse(
        String countryCode, BigDecimal amount, String currency, boolean isActive
) {

    public static AssetPriceResponse of(MotifAssetPrice price, String countryCode) {
        return new AssetPriceResponse(countryCode, price.getAmount(), price.getCurrency(), price.isActive());
    }
}
