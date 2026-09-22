package com.ssafy.motif.core.network.service.challenge

import com.ssafy.motif.core.network.model.challenge.ChallengeDetailResponse
import com.ssafy.motif.core.network.model.challenge.ChallengeFeedResponse
import javax.inject.Inject

class ChallengeClient @Inject constructor(
    private val challengeService: ChallengeService,
) {

    suspend fun getChallengeFeed(
        cursor: String? = null,
        size: Int = DEFAULT_FEED_SIZE,
    ): ChallengeFeedResponse =
        challengeService.getChallengeFeed(
            cursor = cursor,
            size = size,
        ).data

    suspend fun getChallengeDetail(
        id: Long,
    ): ChallengeDetailResponse =
        challengeService.getChallengeDetail(
            challengeSeq = id,
        ).data

    suspend fun updateChallengeLike(
        id: Long,
        isLiked: Boolean,
    ) {
        if (isLiked) {
            challengeService.postChallengeLike(
                challengeSeq = id,
            )
        } else {
            challengeService.deleteChallengeLike(
                challengeSeq = id,
            )
        }
    }

    private companion object {
        const val DEFAULT_FEED_SIZE = 20
    }
}