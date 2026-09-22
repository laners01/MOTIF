package com.ssafy.motif.core.data.repository

import com.ssafy.motif.core.data.model.CursorPage
import com.ssafy.motif.core.model.challenge.Challenge
import com.ssafy.motif.core.model.challenge.ChallengeDetail
import com.ssafy.motif.core.model.challenge.ChallengeParticipation
import com.ssafy.motif.core.model.challenge.ParticipationVisibility
import kotlinx.coroutines.flow.Flow

interface ChallengeRepository {

    fun getChallengeFeed(
        cursor: String? = null,
        size: Int = DEFAULT_FEED_SIZE,
    ): Flow<CursorPage<Challenge>>

    fun getChallengeDetail(
        id: Long,
    ): Flow<ChallengeDetail>

    fun updateChallengeLike(
        id: Long,
        isLiked: Boolean,
    ): Flow<Unit>

    fun getChallengeParticipationFeed(
        challengeId: Long,
        cursor: String? = null,
        size: Int = DEFAULT_FEED_SIZE,
    ): Flow<CursorPage<ChallengeParticipation>>

    fun getChallengeParticipation(
        id: Long,
    ): Flow<ChallengeParticipation>

    fun publishChallengeParticipation(
        jobUuid: String,
        description: String,
        visibility: ParticipationVisibility,
        commentEnabled: Boolean,
    ): Flow<Long>

    fun updateChallengeParticipation(
        id: Long,
        description: String? = null,
        visibility: ParticipationVisibility? = null,
        commentEnabled: Boolean? = null,
    ): Flow<ChallengeParticipation>

    fun deleteChallengeParticipation(
        id: Long,
    ): Flow<Unit>

    fun updateChallengeParticipationLike(
        id: Long,
        isLiked: Boolean,
    ): Flow<Unit>

    fun hideChallengeParticipation(
        id: Long,
    ): Flow<Unit>

    fun reportChallengeParticipation(
        id: Long,
        reasonCode: String,
    ): Flow<Unit>

    private companion object {
        const val DEFAULT_FEED_SIZE = 20
    }
}