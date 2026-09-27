package com.ssafy.motif.core.network.model.challenge

import kotlinx.serialization.Serializable

@Serializable
data class ChallengeDetailResponse(
    val challengeSeq: Long,
    val videoUrl: String,
    val thumbnailUrl: String,
    val durationMs: Long,
    val artist: ChallengeArtistResponse,
    val song: ChallengeSongResponse,
    val viewCount: Long,
    val participantCount: Long,
    val likeCount: Long,
    val commentCount: Long,
    val isLiked: Boolean,
    val isSubscribed: Boolean,
    val description: String,
)
