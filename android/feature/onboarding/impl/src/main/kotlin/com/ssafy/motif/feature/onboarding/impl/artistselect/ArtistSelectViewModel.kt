package com.ssafy.motif.feature.onboarding.impl.artistselect

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

class ArtistSelectViewModel @Inject constructor(): ViewModel() {
    private val _uiState = MutableStateFlow(
        ArtistSelectUiState(artists = ArtistMokData.artists),
    )
    val uiState: StateFlow<ArtistSelectUiState> = _uiState.asStateFlow()
}