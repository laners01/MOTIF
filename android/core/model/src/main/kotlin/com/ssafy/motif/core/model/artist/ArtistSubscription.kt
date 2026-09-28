package com.ssafy.motif.core.model.artist

data class ArtistSubscription(
    var artist: Artist,
    val hasMotifPass: Boolean,
    val subscribedDays: Int,        // 구독일 수
    val challengeCount: Long,       // 함께 찍은 챌린지 수
    val ownedMotifCount: Long,      // 보유 모티프 수
    val totalMotifCount: Long,      // 해당 아티스트의 전체 모티프 수
)