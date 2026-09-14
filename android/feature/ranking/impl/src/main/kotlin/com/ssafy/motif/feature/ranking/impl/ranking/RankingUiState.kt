package com.ssafy.motif.feature.ranking.impl.ranking

import androidx.compose.runtime.Immutable
import com.ssafy.motif.core.model.onboarding.OnboardingArtist
import com.ssafy.motif.core.model.ranking.ChallengeRankCard

@Immutable
data class RankingUiState(
    val ranking: List<ChallengeRankCard> = emptyList(),
    val query:String = ""
){
    val filteredChallengeCard: List<ChallengeRankCard>
        get() = if (query.isBlank()) {
            ranking
        } else {
            ranking.filter {
                it.name.contains(query, ignoreCase = true) ||
                        it.groupName.contains(query, ignoreCase = true) ||
                        it.challengeName.contains(query,ignoreCase = true)
            }
        }
}