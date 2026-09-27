package com.ssafy.motif.core.network.model.order

import kotlinx.serialization.Serializable

@Serializable
data class CreateOrderRequest(
    val productType: OrderProductTypeRequest,
    val assetSeq: Long? = null,
    val artistSeq: Long? = null,
    val passDurationMonths: Int? = null,
)

@Serializable
enum class OrderProductTypeRequest {
    ASSET,
    PASS,
}

@Serializable
data class ConfirmOrderRequest(
    val paymentKey: String,
    val orderId: String,
    val amount: String,
)