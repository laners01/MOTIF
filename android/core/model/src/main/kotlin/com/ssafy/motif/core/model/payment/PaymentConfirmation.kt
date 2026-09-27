package com.ssafy.motif.core.model.payment

sealed interface PaymentConfirmation {

    val orderId: String

    data class Asset(
        override val orderId: String,
        val assetId: Long,
    ) : PaymentConfirmation

    data class Pass(
        override val orderId: String,
        val expiresAt: Long,
    ) : PaymentConfirmation
}