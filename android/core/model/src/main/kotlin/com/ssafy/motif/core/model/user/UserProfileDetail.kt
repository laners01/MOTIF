package com.ssafy.motif.core.model.user

import com.ssafy.motif.core.model.artist.Artist

data class UserProfileDetail(
    val profile: UserProfile,
    val subscribedArtists: List<Artist>,
)