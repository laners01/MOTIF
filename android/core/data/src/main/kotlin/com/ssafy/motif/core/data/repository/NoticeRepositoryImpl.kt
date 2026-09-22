package com.ssafy.motif.core.data.repository

import com.ssafy.motif.core.common.network.Dispatcher
import com.ssafy.motif.core.common.network.MotifDispatcher
import com.ssafy.motif.core.network.service.notice.NoticeClient
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject

internal class NoticeRepositoryImpl @Inject constructor(
    private val noticeClient: NoticeClient,
    @param:Dispatcher(MotifDispatcher.IO)
    private val ioDispatcher: CoroutineDispatcher,
) : NoticeRepository {

    override fun updateNoticeLike(
        id: Long,
        isLiked: Boolean,
    ): Flow<Unit> =
        flow {
            noticeClient.updateNoticeLike(
                noticeId = id,
                isLiked = isLiked,
            )
            emit(Unit)
        }.flowOn(ioDispatcher)
}