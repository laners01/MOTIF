package com.ssafy.motif.core.network.model.order

import kotlinx.serialization.Serializable

@Serializable
data class CreateOrderResponse(
    val orderUuid: String,
    val orderName: String,
    val amount: String,
    val currency: String,
    val expiresDttm: String,
)

@Serializable
data class ConfirmOrderResponse(
    val orderUuid: String,
    val status: String,
    val productType: OrderProductTypeResponse,
    val asset: ConfirmedAssetResponse? = null,
    val pass: ConfirmedPassResponse? = null,
)

@Serializable
enum class OrderProductTypeResponse {
    ASSET,
    PASS,
}

@Serializable
data class ConfirmedAssetResponse(
    val assetSeq: Long,
)

@Serializable
data class ConfirmedPassResponse(
    val expiresDttm: String,
)

@Serializable
data class CancelOrderResponse(
    val orderUuid: String,
    val status: String,
)