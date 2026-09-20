package com.ssafy.motif.core.model.shop

/**
 * 구독권(PASS) 결제 플랜.
 *
 * @param months 구독 기간 (개월).
 * @param monthlyPrice 월 환산 가격 (원).
 * @param note 플랜 부가 안내 (예: + 시크릿 에셋). 없으면 null.
 */
data class PassPlan(
    val id: Long,
    val months: Int,
    val monthlyPrice: Int,
    val note: String? = null,
)
