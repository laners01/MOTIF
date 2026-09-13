package com.ssafy.motif.core.model.ranking

data class ChallengeRankCard(
    val id: Long,
    val name: String,
    val groupName: String,
    val profileImageUrl: String?,
    val ranking: Long,
    val challengeName: String,
    val rankingChanged: Long,
)
