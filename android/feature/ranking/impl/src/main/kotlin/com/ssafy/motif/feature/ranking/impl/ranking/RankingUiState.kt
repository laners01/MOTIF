package com.ssafy.motif.feature.ranking.impl.ranking

import androidx.compose.runtime.Immutable
import com.ssafy.motif.core.model.ranking.ChallengeRankCard

@Immutable
data class RankingUiState(
    val ranking: List<ChallengeRankCard> = emptyList(),
    val query:String = ""
)