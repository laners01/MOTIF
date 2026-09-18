package com.ssafy.motif.core.model.mypage

data class LikedChallenge(
    val id: Long,
    val videoUrl:String,
    val thumbnailUrl: String,
    val likeCount: Long,
    val likedAt: Long,
)