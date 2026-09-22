package com.ssafy.motif.core.model.challenge

import com.ssafy.motif.core.model.artist.Artist

data class Challenge(
    val id: Long,
    val videoUrl: String,
    val thumbnailUrl: String,
    val artist: Artist,
    val description: String,
    val song: ChallengeSong,
    val likeCount: Long,
    val commentCount: Long,
    val participantCount: Long,
    val viewCount: Long,
    val createdAt: Long,
    val isLiked: Boolean,
    val isSubscribed: Boolean,
    val challengeArtists: List<Artist>,
)