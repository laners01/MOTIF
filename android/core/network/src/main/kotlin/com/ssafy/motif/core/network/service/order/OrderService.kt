package com.ssafy.motif.core.network.service.order

import com.ssafy.motif.core.network.model.BaseResponse
import com.ssafy.motif.core.network.model.order.CancelOrderResponse
import com.ssafy.motif.core.network.model.order.ConfirmOrderRequest
import com.ssafy.motif.core.network.model.order.ConfirmOrderResponse
import com.ssafy.motif.core.network.model.order.CreateOrderRequest
import com.ssafy.motif.core.network.model.order.CreateOrderResponse
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path

interface OrderService {

    @POST("/api/v1/orders")
    suspend fun createOrder(
        @Body request: CreateOrderRequest,
    ): BaseResponse<CreateOrderResponse>

    @POST("/api/v1/orders/{orderUuid}/confirm")
    suspend fun confirmOrder(
        @Path("orderUuid")
        orderUuid: String,
        @Body
        request: ConfirmOrderRequest,
    ): BaseResponse<ConfirmOrderResponse>

    @POST("/api/v1/orders/{orderUuid}/cancel")
    suspend fun cancelOrder(
        @Path("orderUuid")
        orderUuid: String,
    ): BaseResponse<CancelOrderResponse>
}