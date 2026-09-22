package com.ssafy.motif.feature.onboarding.impl.onboarding

import androidx.compose.runtime.Immutable
import com.ssafy.motif.core.model.artist.Artist

enum class OnboardingStep {
    ArtistSelect,
    NicknameInput,
}

@Immutable
data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.ArtistSelect,
    val artists: List<Artist> = emptyList(),
    val selectedArtists: Set<Long> = emptySet(),
    val query: String = "",
    val nickname: String = "",
) {
    val filteredArtists: List<Artist>
        get() = if (query.isBlank()) {
            artists
        } else {
            artists.filter {
                it.name.contains(query, ignoreCase = true) ||
                        it.groupName?.contains(query, ignoreCase = true) == true
            }
        }

    val likedArtists: List<Artist>
        get() = artists.filter { it.id in selectedArtists }

    val canProceed: Boolean
        get() = when (step) {
            OnboardingStep.ArtistSelect -> selectedArtists.size >= MIN_SELECTION_COUNT
            OnboardingStep.NicknameInput -> nickname.isNotEmpty()
        }

    companion object {
        const val MIN_SELECTION_COUNT = 1
        const val MAX_NICKNAME_LENGTH = 20
    }
}
