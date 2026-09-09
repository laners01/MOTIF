package com.ssafy.motif.feature.onboarding.impl.artistselect

import androidx.compose.runtime.Immutable

@Immutable
data class ArtistUiModel(
    val id: Long,
    val name: String,
    val groupName:String,
    val profileImageUrl:String?,
)

@Immutable
data class ArtistSelectUiState(
    val artists: List<ArtistUiModel> = emptyList(),
)