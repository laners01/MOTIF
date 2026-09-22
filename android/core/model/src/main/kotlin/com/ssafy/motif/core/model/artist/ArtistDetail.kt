package com.ssafy.motif.core.model.artist

data class ArtistDetail(
    val artist: Artist,
    val introduction: String,
    val subscriberCount: Long,
    val challengeCount: Long,
    val isSubscribed: Boolean,
    val isAlarmOn: Boolean,
    val hasMotifPass: Boolean,
)