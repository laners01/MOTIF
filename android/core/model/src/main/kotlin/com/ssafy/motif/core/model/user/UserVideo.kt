package com.ssafy.motif.core.model.user

import com.ssafy.motif.core.model.challenge.ParticipationVisibility

data class UserVideo(
    val id: Long,
    val thumbnailUrl: String,
    val viewCount: Long,
    val visibility: ParticipationVisibility,
)