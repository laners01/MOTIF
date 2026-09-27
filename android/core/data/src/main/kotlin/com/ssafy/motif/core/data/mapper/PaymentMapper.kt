package com.ssafy.motif.core.data.mapper

import com.ssafy.motif.core.model.payment.PaymentConfirmation
import com.ssafy.motif.core.model.payment.PaymentOrder
import com.ssafy.motif.core.network.model.order.ConfirmOrderResponse
import com.ssafy.motif.core.network.model.order.CreateOrderResponse
import com.ssafy.motif.core.network.model.order.OrderProductTypeResponse
import java.time.Instant

internal fun CreateOrderResponse.toModel():
        PaymentOrder =
    PaymentOrder(
        id = orderUuid,
        name = orderName,
        amount = amount,
        currency = currency,
        expiresAt =
            Instant
                .parse(
                    expiresDttm,
                )
                .toEpochMilli(),
    )

internal fun ConfirmOrderResponse.toModel():
        PaymentConfirmation =
    when (productType) {
        OrderProductTypeResponse.ASSET -> {
            val asset =
                requireNotNull(
                    asset,
                ) {
                    "ASSET confirmation must contain asset."
                }

            PaymentConfirmation.Asset(
                orderId = orderUuid,
                assetId = asset.assetSeq,
            )
        }

        OrderProductTypeResponse.PASS -> {
            val pass =
                requireNotNull(
                    pass,
                ) {
                    "PASS confirmation must contain pass."
                }

            PaymentConfirmation.Pass(
                orderId = orderUuid,
                expiresAt =
                    Instant
                        .parse(
                            pass.expiresDttm,
                        )
                        .toEpochMilli(),
            )
        }
    }