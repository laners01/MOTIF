package com.ssafy.motif.core.network.model.challenge

import kotlinx.serialization.Serializable

@Serializable
enum class VideoVisibility {
    PUBLIC,
    PRIVATE,
}