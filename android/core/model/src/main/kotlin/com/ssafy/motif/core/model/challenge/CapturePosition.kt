package com.ssafy.motif.core.model.challenge

import com.ssafy.motif.core.model.artist.Artist

data class CapturePosition(
    val id: Long,
    val artist: Artist,
    val slotOrder: Int,
    val x: Float?,
    val y: Float?,
    val initialSceneUrl: String?,
    val previewImageUrl: String?,
) {
    val isAvailable: Boolean
        get() = initialSceneUrl != null
}