package com.ssafy.motif.core.model.motif

import com.ssafy.motif.core.model.artist.Artist

data class Motif(
    val id: Long,
    val title: String,
    val description: String,
    val artist: Artist,
    val videoUrl: String,
    val thumbnailUrl: String,
    val createdAt: Long,
)