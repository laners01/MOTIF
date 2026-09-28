package com.ssafy.motif.core.data.repository

import com.ssafy.motif.core.common.network.Dispatcher
import com.ssafy.motif.core.common.network.MotifDispatcher
import com.ssafy.motif.core.data.mapper.toModel
import com.ssafy.motif.core.model.payment.PaymentConfirmation
import com.ssafy.motif.core.model.payment.PaymentOrder
import com.ssafy.motif.core.network.service.order.OrderClient
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

internal class PaymentRepositoryImpl @Inject constructor(
    private val orderClient: OrderClient,
    @param:Dispatcher(MotifDispatcher.IO)
    private val ioDispatcher: CoroutineDispatcher,
) : PaymentRepository {

    override fun createAssetOrder(
        assetId: Long,
    ): Flow<PaymentOrder> =
        flow {
            val response =
                orderClient
                    .createAssetOrder(
                        assetId = assetId,
                    )

            emit(
                response.toModel(),
            )
        }.flowOn(
            ioDispatcher,
        )

    override fun createPassOrder(
        artistId: Long,
        durationMonths: Int,
    ): Flow<PaymentOrder> =
        flow {
            val response =
                orderClient
                    .createPassOrder(
                        artistId = artistId,
                        durationMonths =
                            durationMonths,
                    )

            emit(
                response.toModel(),
            )
        }.flowOn(
            ioDispatcher,
        )

    override fun confirmOrder(
        orderId: String,
        paymentKey: String,
        amount: String,
    ): Flow<PaymentConfirmation> =
        flow {
            val response =
                orderClient
                    .confirmOrder(
                        orderId = orderId,
                        paymentKey = paymentKey,
                        amount = amount,
                    )

            emit(
                response.toModel(),
            )
        }.flowOn(
            ioDispatcher,
        )

    override fun cancelOrder(
        orderId: String,
    ): Flow<Unit> =
        flow {
            orderClient
                .cancelOrder(
                    orderId = orderId,
                )

            emit(Unit)
        }.flowOn(
            ioDispatcher,
        )
}