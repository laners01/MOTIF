package com.ssafy.motif.feature.ranking.impl.ranking

import androidx.compose.runtime.Immutable
import com.ssafy.motif.core.model.challenge.ChallengeRank

@Immutable
data class RankingUiState(
    val ranking: List<ChallengeRank> = emptyList(),
    val query: String = "",
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val hasNext: Boolean = false,
    val nextPage: Int = 0,
    val isError: Boolean = false,
) {
    val filteredRanking: List<ChallengeRank>
        get() =
            if (query.isBlank()) {
                ranking
            } else {
                ranking.filter { challenge ->
                    challenge.artist.name.contains(
                        other = query,
                        ignoreCase = true,
                    ) ||
                            challenge.artist.groupName
                                ?.contains(
                                    other = query,
                                    ignoreCase = true,
                                ) == true ||
                            challenge.song
                                ?.title
                                ?.contains(
                                    other = query,
                                    ignoreCase = true,
                                ) == true ||
                            challenge.song
                                ?.artistName
                                ?.contains(
                                    other = query,
                                    ignoreCase = true,
                                ) == true
                }
            }
}