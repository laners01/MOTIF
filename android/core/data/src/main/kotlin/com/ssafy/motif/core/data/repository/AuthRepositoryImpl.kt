package com.ssafy.motif.core.data.repository

import com.ssafy.motif.core.common.network.Dispatcher
import com.ssafy.motif.core.common.network.MotifDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject

class AuthRepositoryImpl @Inject constructor(
    @param:Dispatcher(MotifDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : AuthRepository {

    override fun signInWithGoogle(
        idToken: String,
    ): Flow<Unit> =
        flow {
            require(idToken.isNotBlank())

            // TODO 서버 API 연동
            // authRemoteDataSource.signInWithGoogle(idToken)

            emit(Unit)
        }.flowOn(ioDispatcher)
}