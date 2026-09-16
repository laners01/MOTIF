package com.ssafy.motif.core.model.challenges

import com.ssafy.motif.core.model.home.Artist

data class ChallengeShortForm(
    val id: Long,
    val videoUrl: String,
    val thumbnailUrl: String,
    val artist: Artist,
    val description: String,
    val songArtistName: String,
    val songTitle: String,
    val likeCount: Long,
    val commentCount: Long,
    val participantCount: Long,
    val viewCount: Long,
    val createdAt: Long,
    val isLiked: Boolean,
    val isSubscribed: Boolean,
    val challengeArtists: List<Artist>,
)