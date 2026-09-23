package com.ssafy.motif.feature.onboarding.impl.onboarding

import androidx.compose.runtime.Immutable
import com.ssafy.motif.core.model.artist.Artist

enum class OnboardingStep {
    ArtistSelect,
    NicknameInput,
}

enum class OnboardingError {
    ArtistLoadFailed,
    SearchFailed,
    ArtistNotFound,
    InvalidParameter,
    SubmitFailed,
}

@Immutable
data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.ArtistSelect,
    val artists: List<Artist> = emptyList(),
    val searchResults: List<Artist> = emptyList(),
    val selectedArtists: Set<Long> = emptySet(),
    val selectedArtistItems: List<Artist> = emptyList(),
    val query: String = "",
    val nickname: String = "",
    val isLoading: Boolean = false,
    val isSearching: Boolean = false,
    val isSubmitting: Boolean = false,
    val isCompleted: Boolean = false,
    val error: OnboardingError? = null,
) {

    val filteredArtists: List<Artist>
        get() =
            if (query.isBlank()) {
                artists
            } else {
                searchResults
            }

    val likedArtists: List<Artist>
        get() = selectedArtistItems

    val canProceed: Boolean
        get() =
            when (step) {
                OnboardingStep.ArtistSelect -> {
                    selectedArtists.size >= MIN_SELECTION_COUNT
                }

                OnboardingStep.NicknameInput -> {
                    selectedArtists.size >= MIN_SELECTION_COUNT &&
                            NICKNAME_PATTERN.matches(nickname) &&
                            !isSubmitting
                }
            }

    companion object {
        const val MIN_SELECTION_COUNT = 1
        const val MAX_NICKNAME_LENGTH = 20

        private val NICKNAME_PATTERN =
            Regex(
                "^[a-zA-Z0-9가-힣._]{2,20}$",
            )
    }
}