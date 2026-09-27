package com.ssafy.motif.core.data.repository

import com.ssafy.motif.core.common.network.Dispatcher
import com.ssafy.motif.core.common.network.MotifDispatcher
import com.ssafy.motif.core.network.service.notification.NotificationClient
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

class NotificationRepositoryImpl @Inject constructor(
    private val notificationClient: NotificationClient,
    @param:Dispatcher(MotifDispatcher.IO)
    private val ioDispatcher: CoroutineDispatcher,
) : NotificationRepository {

    override fun registerDeviceToken(
        fcmToken: String,
    ): Flow<Unit> =
        flow {
            notificationClient.registerDeviceToken(
                fcmToken = fcmToken,
            )

            emit(Unit)
        }.flowOn(ioDispatcher)

    override fun deactivateDeviceToken(
        fcmToken: String,
    ): Flow<Unit> =
        flow {
            notificationClient.deactivateDeviceToken(
                fcmToken = fcmToken,
            )

            emit(Unit)
        }.flowOn(ioDispatcher)
}