package com.ssafy.motif.core.network.model.challenge

import kotlinx.serialization.Serializable

@Serializable
data class ParticipationVideoResponse(
    val videoSeq: Long,
    val challengeSeq: Long,
    val description: String,
    val videoUrl: String,
    val thumbnailUrl: String,
    val durationMs: Long,
    val author: ParticipationVideoAuthorResponse,
    val viewCount: Long,
    val likeCount: Long,
    val commentCount: Long,
    val hasArtistLike: Boolean,
    val hasArtistComment: Boolean,
    val isLiked: Boolean,
    val commentEnabled: Boolean,
    val visibility: VideoVisibility,
    val isMine: Boolean,
    val regDttm: String,
)