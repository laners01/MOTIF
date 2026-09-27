package com.ssafy.motif.core.model.motif

import com.ssafy.motif.core.model.artist.Artist

data class SavedMotif(
    val id: Long,
    val videoUrl: String,
    val thumbnailUrl: String,
    val description: String,
    val artist: Artist,
    val savedAt: Long,
    val isRepresentative: Boolean = false,
    val durationMs: Long = 0L,
    val assetId: Long? = null,
    val assetTitle: String? = null,
)