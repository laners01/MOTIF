package com.ssafy.motif.feature.ranking.impl.ranking

import androidx.compose.runtime.Immutable

@Immutable
data class ChallengeUiModel(
    val id: Long,
    val name: String,
    val groupName:String,
    val profileImageUrl:String?,
    val ranking: Long,
    val challengeName: String,
    val rankingChanged: Long,
)

@Immutable
data class RankingUiState(
    val ranking: List<ChallengeUiModel> = emptyList(),
    val query:String = ""
)