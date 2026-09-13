package com.ssafy.motif.feature.onboarding.impl.onboarding

import androidx.compose.runtime.Immutable

@Immutable
data class ArtistUiModel(
    val id: Long,
    val name: String,
    val groupName: String,
    val profileImageUrl: String?,
)

enum class OnboardingStep {
    ArtistSelect,
    NicknameInput,
}

@Immutable
data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.ArtistSelect,
    val artists: List<ArtistUiModel> = emptyList(),
    val selectedArtists: Set<Long> = emptySet(),
    val query: String = "",
    val nickname: String = "",
) {
    val filteredArtists: List<ArtistUiModel>
        get() = if (query.isBlank()) {
            artists
        } else {
            artists.filter {
                it.name.contains(query, ignoreCase = true) ||
                        it.groupName.contains(query, ignoreCase = true)
            }
        }

    val likedArtists: List<ArtistUiModel>
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
