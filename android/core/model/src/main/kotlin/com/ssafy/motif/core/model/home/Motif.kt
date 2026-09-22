package com.ssafy.motif.core.model.home

import com.ssafy.motif.core.model.artist.Artist

data class Motif(
    val id: Long,
    val thumbnailUrl: String,
    val title: String,
    val artist: Artist,
    val createdAt: Long,
)