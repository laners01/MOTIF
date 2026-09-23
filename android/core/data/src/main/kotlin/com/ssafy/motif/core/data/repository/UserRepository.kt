package com.ssafy.motif.core.data.repository

import com.ssafy.motif.core.data.model.CursorPage
import com.ssafy.motif.core.model.user.UserActivity
import com.ssafy.motif.core.model.user.UserOverview
import com.ssafy.motif.core.model.user.UserProfileDetail
import com.ssafy.motif.core.model.user.UserSubscription
import com.ssafy.motif.core.model.user.UserVideo
import kotlinx.coroutines.flow.Flow

interface UserRepository {

    fun getUserOverview(): Flow<UserOverview>

    fun updateNickname(
        nickname: String,
    ): Flow<String>

    fun updateProfileImage(
        contentType: String,
        imageBytes: ByteArray,
    ): Flow<String>

    fun getUserProfile(
        id: Long,
    ): Flow<UserProfileDetail>

    fun getSubscriptions(): Flow<List<UserSubscription>>

    fun getVideos(): Flow<List<UserVideo>>

    fun getActivities(
        cursor: String? = null,
        size: Int = DEFAULT_ACTIVITY_SIZE,
    ): Flow<CursorPage<UserActivity>>

    companion object {
        const val DEFAULT_ACTIVITY_SIZE = 20
    }
}