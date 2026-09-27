package com.ssafy.motif.core.model.shop

data class PassPlan(
    val id: Long,
    val months: Int,
    val monthlyPrice: Int,
    val totalPrice: Int = monthlyPrice * months,
    val currency: String = "KRW",
    val note: String? = null,
)