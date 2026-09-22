package com.ssafy.motif.core.model.home

import com.ssafy.motif.core.model.artist.Artist

data class Notice(
    val id: Long,
    val artist: Artist,
    val content: String,
    val imageUrls: List<String>,
    val likeCount: Long,
    val commentCount: Long,
    val isLiked: Boolean,
    val createdAt: Long,
)