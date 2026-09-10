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
    val selectedArtists: Set<Long> = emptySet(),
    val query: String = ""
){
    val filteredArtists: List<ArtistUiModel>
        get() = if(query.isBlank()){
            artists
        }else{
            artists.filter {
                it.name.contains(query,ignoreCase=true)||
                        it.groupName.contains(query,ignoreCase = true)
            }
        }

    val canProceed: Boolean
        get() = selectedArtists.size>= MIN_SELECTION_COUNT

    companion object{
        const val MIN_SELECTION_COUNT = 1
    }
}