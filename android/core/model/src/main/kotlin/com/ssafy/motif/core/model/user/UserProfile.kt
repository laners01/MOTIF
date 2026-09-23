package com.ssafy.motif.core.model.user

data class UserProfile(
    val id: Long,
    val nickname: String,
    val profileImageUrl: String?,
)