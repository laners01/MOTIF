package com.ssafy.motif.core.data.repository

import com.ssafy.motif.core.model.payment.PaymentConfirmation
import com.ssafy.motif.core.model.payment.PaymentOrder
import kotlinx.coroutines.flow.Flow

interface PaymentRepository {

    fun createAssetOrder(
        assetId: Long,
    ): Flow<PaymentOrder>

    fun createPassOrder(
        artistId: Long,
        durationMonths: Int,
    ): Flow<PaymentOrder>

    fun confirmOrder(
        orderId: String,
        paymentKey: String,
        amount: String,
    ): Flow<PaymentConfirmation>

    fun cancelOrder(
        orderId: String,
    ): Flow<Unit>
}