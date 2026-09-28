package com.ssafy.motif.core.network.model.user

import kotlinx.serialization.Serializable

@Serializable
data class UpdateNicknameRequest(
    val nickname: String,
)

@Serializable
data class ProfileImagePresignRequest(
    val contentType: String,
    val contentLength: Long,
)

@Serializable
data class UpdateProfileImageRequest(
    val imageKey: String,
)