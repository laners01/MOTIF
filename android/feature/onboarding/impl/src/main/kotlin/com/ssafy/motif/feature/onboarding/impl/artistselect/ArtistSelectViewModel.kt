package com.ssafy.motif.feature.onboarding.impl.artistselect

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

class ArtistSelectViewModel @Inject constructor(): ViewModel() {
    private val _uiState = MutableStateFlow(
        ArtistSelectUiState(artists = ArtistMokData.artists),
    )
    val uiState: StateFlow<ArtistSelectUiState> = _uiState.asStateFlow()


    fun toggleArtist(artistId: Long) {
        _uiState.update { state ->
            val selected = state.selectedArtists
            state.copy(
                selectedArtists = if (artistId in selected) {
                    selected - artistId
                } else {
                    selected + artistId
                },
            )
        }
    }
}