package com.ssafy.motif.core.model.artist

data class RankedArtist(
    val artist: Artist,
    val rank: Int,
    val rankDelta: Int?,
    val isSubscribed: Boolean,
)

data class ArtistWithSubscription(
    val artist: Artist,
    val isSubscribed: Boolean,
)

data class ArtistPass(
    val active: Boolean,
    val expiresAt: Long?,
)

data class ArtistMotifVideo(
    val id: Long,
    val videoUrl: String,
    val thumbnailUrl: String,
    val durationMs: Long,
)

data class ArtistSubscriptionResult(
    val isSubscribed: Boolean,
    val subscriberCount: Long,
)

enum class ArtistFilter {
    ALL,
    SUBSCRIBED,
    UNSUBSCRIBED,
}