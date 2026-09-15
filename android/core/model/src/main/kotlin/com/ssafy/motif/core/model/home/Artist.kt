package com.ssafy.motif.core.model.home

data class Artist(
    val id: Long,
    val name: String,
    val profileImageUrl: String,
    val groupName: String?,
)