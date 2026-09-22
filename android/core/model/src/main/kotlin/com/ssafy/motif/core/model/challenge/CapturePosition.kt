package com.ssafy.motif.core.model.challenge

import com.ssafy.motif.core.model.artist.Artist

data class CapturePosition(
    val artist: Artist,
    val x: Float,
    val y: Float,
    val previewImageUrl: String,
    val videoUrl: String,
)