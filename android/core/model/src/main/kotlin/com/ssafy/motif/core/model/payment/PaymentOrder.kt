package com.ssafy.motif.core.model.payment

data class PaymentOrder(
    val id: String,
    val name: String,
    val amount: String,
    val currency: String,
    val expiresAt: Long,
)