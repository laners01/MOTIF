package com.ssafy.motif.feature.motif.impl.archive

import androidx.compose.runtime.Immutable
import com.ssafy.motif.core.model.artist.Artist
import com.ssafy.motif.core.model.motif.SavedMotif

internal sealed interface ArchiveUiState {

    data object Loading : ArchiveUiState

    @Immutable
    data class Success(
        val motifs: List<SavedMotif>,
        val artists: List<Artist>,
        val selectedArtistId: Long? = null,
        val selectedMotif: SavedMotif?,
        val nextCursor: String? = null,
        val hasNext: Boolean,
        val appendState:
        ArchiveAppendUiState =
            ArchiveAppendUiState.Idle,
        val updatingRepresentativeMotifId:
        Long? = null,
    ) : ArchiveUiState

    data class Error(
        val exception: Throwable,
    ) : ArchiveUiState
}

internal sealed interface ArchiveAppendUiState {

    data object Idle :
        ArchiveAppendUiState

    data object Loading :
        ArchiveAppendUiState

    data class Error(
        val exception: Throwable,
    ) : ArchiveAppendUiState
}