package com.ssafy.motif.core.model.artist

data class Artist(
    val id: Long,
    val name: String,
    val profileImageUrl: String,
    val groupName: String?,
)