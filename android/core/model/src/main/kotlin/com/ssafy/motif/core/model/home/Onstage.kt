package com.ssafy.motif.core.model.home

data class Onstage(
    val videoId: Long,
    val rank: Int,
    val videoUrl: String,
    val thumbnailUrl: String,
    val songArtistName: String,
    val songTitle: String,
)