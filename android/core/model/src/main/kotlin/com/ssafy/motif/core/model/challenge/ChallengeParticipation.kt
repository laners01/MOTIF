package com.ssafy.motif.core.model.challenge

import com.ssafy.motif.core.model.user.UserProfile

data class ChallengeParticipation(
    val id: Long,
    val challengeId: Long,
    val description: String,
    val videoUrl: String,
    val thumbnailUrl: String,
    val durationMs: Long,
    val author: UserProfile,
    val viewCount: Long,
    val likeCount: Long,
    val commentCount: Long,
    val hasArtistLike: Boolean,
    val hasArtistComment: Boolean,
    val isLiked: Boolean,
    val commentEnabled: Boolean,
    val visibility: ParticipationVisibility,
    val isMine: Boolean,
    val createdAt: Long,
)

enum class ParticipationVisibility {
    PUBLIC,
    PRIVATE,
}