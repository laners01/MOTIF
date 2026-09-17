package com.ssafy.motif.core.model.mypage

data class UserProfile(
    val id: Long,
    val nickname: String,
    val profileImageUrl: String?
)