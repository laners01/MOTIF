package com.ssafy.motif.core.data.repository

import com.ssafy.motif.core.common.network.Dispatcher
import com.ssafy.motif.core.common.network.MotifDispatcher
import com.ssafy.motif.core.data.mapper.toModel
import com.ssafy.motif.core.data.model.CursorPage
import com.ssafy.motif.core.model.user.UserActivity
import com.ssafy.motif.core.model.user.UserOverview
import com.ssafy.motif.core.model.user.UserProfileDetail
import com.ssafy.motif.core.model.user.UserSubscription
import com.ssafy.motif.core.model.user.UserVideo
import com.ssafy.motif.core.network.service.user.UserClient
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

internal class UserRepositoryImpl @Inject constructor(
    private val userClient: UserClient,
    @param:Dispatcher(MotifDispatcher.IO)
    private val ioDispatcher: CoroutineDispatcher,
) : UserRepository {

    override fun getUserOverview(): Flow<UserOverview> =
        flow {
            val response =
                userClient.getMe()

            emit(response.toModel())
        }.flowOn(ioDispatcher)

    override fun updateNickname(
        nickname: String,
    ): Flow<String> =
        flow {
            val updatedNickname =
                userClient.updateNickname(
                    nickname = nickname,
                )

            emit(updatedNickname)
        }.flowOn(ioDispatcher)

    override fun updateProfileImage(
        contentType: String,
        imageBytes: ByteArray,
    ): Flow<String> =
        flow {
            val presign =
                userClient.createProfileImagePresign(
                    contentType = contentType,
                    contentLength =
                        imageBytes.size.toLong(),
                )

            userClient.uploadProfileImage(
                uploadUrl = presign.uploadUrl,
                contentType = contentType,
                imageBytes = imageBytes,
            )

            val profileImageUrl =
                userClient.updateProfileImage(
                    imageKey = presign.imageKey,
                )

            emit(profileImageUrl)
        }.flowOn(ioDispatcher)

    override fun getUserProfile(
        id: Long,
    ): Flow<UserProfileDetail> =
        flow {
            val response =
                userClient.getUserProfile(
                    id = id,
                )

            emit(response.toModel())
        }.flowOn(ioDispatcher)

    override fun getSubscriptions():
            Flow<List<UserSubscription>> =
        flow {
            val response =
                userClient.getSubscriptions()

            emit(response.toModel())
        }.flowOn(ioDispatcher)

    override fun getVideos(): Flow<List<UserVideo>> =
        flow {
            val response =
                userClient.getVideos()

            emit(response.toModel())
        }.flowOn(ioDispatcher)

    override fun getActivities(
        cursor: String?,
        size: Int,
    ): Flow<CursorPage<UserActivity>> =
        flow {
            val response =
                userClient.getActivities(
                    cursor = cursor,
                    size = size,
                )

            emit(response.toModel())
        }.flowOn(ioDispatcher)
}