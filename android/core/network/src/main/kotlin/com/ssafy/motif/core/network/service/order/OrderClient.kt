package com.ssafy.motif.core.network.service.order

import android.util.Log
import com.ssafy.motif.core.network.model.order.ConfirmOrderRequest
import com.ssafy.motif.core.network.model.order.ConfirmOrderResponse
import com.ssafy.motif.core.network.model.order.CreateOrderRequest
import com.ssafy.motif.core.network.model.order.CreateOrderResponse
import com.ssafy.motif.core.network.model.order.OrderProductTypeRequest
import javax.inject.Inject

class OrderClient @Inject constructor(
    private val orderService: OrderService,
) {

    suspend fun createAssetOrder(
        assetId: Long,
    ): CreateOrderResponse {
        val request =
            CreateOrderRequest(
                productType =
                    OrderProductTypeRequest.ASSET,
                assetSeq = assetId,
            )

        Log.d(
            TAG,
            """
            createAssetOrder request
            productType=${request.productType}
            assetSeq=${request.assetSeq}
            artistSeq=${request.artistSeq}
            passDurationMonths=${request.passDurationMonths}
            """.trimIndent(),
        )

        val response =
            orderService
                .createOrder(
                    request = request,
                )
                .data

        Log.d(
            TAG,
            """
            createAssetOrder response
            orderUuid=${response.orderUuid}
            orderName=${response.orderName}
            amount=${response.amount}
            currency=${response.currency}
            expiresDttm=${response.expiresDttm}
            """.trimIndent(),
        )

        return response
    }

    suspend fun createPassOrder(
        artistId: Long,
        durationMonths: Int,
    ): CreateOrderResponse {
        val request =
            CreateOrderRequest(
                productType =
                    OrderProductTypeRequest.PASS,
                artistSeq = artistId,
                passDurationMonths =
                    durationMonths,
            )

        Log.d(
            TAG,
            """
            createPassOrder request
            productType=${request.productType}
            assetSeq=${request.assetSeq}
            artistSeq=${request.artistSeq}
            passDurationMonths=${request.passDurationMonths}
            """.trimIndent(),
        )

        val response =
            orderService
                .createOrder(
                    request = request,
                )
                .data

        Log.d(
            TAG,
            """
            createPassOrder response
            orderUuid=${response.orderUuid}
            orderName=${response.orderName}
            amount=${response.amount}
            currency=${response.currency}
            expiresDttm=${response.expiresDttm}
            """.trimIndent(),
        )

        return response
    }

    suspend fun confirmOrder(
        orderId: String,
        paymentKey: String,
        amount: String,
    ): ConfirmOrderResponse {
        val request =
            ConfirmOrderRequest(
                paymentKey = paymentKey,
                orderId = orderId,
                amount = amount,
            )

        Log.d(
            TAG,
            """
            confirmOrder request
            pathOrderUuid=$orderId
            paymentKey=$paymentKey
            orderId=${request.orderId}
            amount=${request.amount}
            """.trimIndent(),
        )

        val response =
            orderService
                .confirmOrder(
                    orderUuid = orderId,
                    request = request,
                )
                .data

        Log.d(
            TAG,
            """
            confirmOrder response
            orderUuid=${response.orderUuid}
            status=${response.status}
            productType=${response.productType}
            """.trimIndent(),
        )

        return response
    }

    suspend fun cancelOrder(
        orderId: String,
    ) {
        Log.d(
            TAG,
            "cancelOrder request orderUuid=$orderId",
        )

        orderService.cancelOrder(
            orderUuid = orderId,
        )

        Log.d(
            TAG,
            "cancelOrder success orderUuid=$orderId",
        )
    }

    private companion object {
        const val TAG =
            "TossPayment"
    }
}