package com.ssafy.motif.core.model.user

data class UserOverview(
    val profile: UserProfile,
    val representativeMotif: RepresentativeMotif?,
    val videos: List<UserVideo>,
)

data class RepresentativeMotif(
    val id: Long,
    val videoUrl: String,
    val thumbnailUrl: String,
)