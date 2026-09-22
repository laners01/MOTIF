package com.ssafy.motif.feature.ranking.impl.ranking

import androidx.compose.runtime.Immutable
import com.ssafy.motif.core.model.challenge.ChallengeRank

@Immutable
data class RankingUiState(
    val ranking: List<ChallengeRank> = emptyList(),
    val query: String = ""
) {
    val filteredChallengeCard: List<ChallengeRank>
        get() = if (query.isBlank()) {
            ranking
        } else {
            ranking.filter {
                it.artist.name.contains(query, ignoreCase = true) ||
                        it.artist.groupName?.contains(query, ignoreCase = true) == true ||
                        it.song.title.contains(query, ignoreCase = true)
            }
        }
}