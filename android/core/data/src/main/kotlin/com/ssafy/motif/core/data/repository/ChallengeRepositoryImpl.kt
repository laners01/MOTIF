package com.ssafy.motif.core.data.repository

import com.ssafy.motif.core.common.network.Dispatcher
import com.ssafy.motif.core.common.network.MotifDispatcher
import com.ssafy.motif.core.data.mapper.toModel
import com.ssafy.motif.core.data.mapper.toNetwork
import com.ssafy.motif.core.data.model.CursorPage
import com.ssafy.motif.core.model.challenge.Challenge
import com.ssafy.motif.core.model.challenge.ChallengeDetail
import com.ssafy.motif.core.model.challenge.ChallengeParticipation
import com.ssafy.motif.core.model.challenge.ParticipationVisibility
import com.ssafy.motif.core.network.service.challenge.ChallengeClient
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject

class ChallengeRepositoryImpl @Inject constructor(
    private val challengeClient: ChallengeClient,
    @param:Dispatcher(MotifDispatcher.IO)
    private val ioDispatcher: CoroutineDispatcher,
) : ChallengeRepository {

    override fun getChallengeFeed(
        cursor: String?,
        size: Int,
    ): Flow<CursorPage<Challenge>> =
        flow {
            val response = challengeClient.getChallengeFeed(
                cursor = cursor,
                size = size,
            )

            emit(response.toModel())
        }.flowOn(ioDispatcher)

    override fun getChallengeDetail(
        id: Long,
    ): Flow<ChallengeDetail> =
        flow {
            val response = challengeClient.getChallengeDetail(
                id = id,
            )

            emit(response.toModel())
        }.flowOn(ioDispatcher)

    override fun updateChallengeLike(
        id: Long,
        isLiked: Boolean,
    ): Flow<Unit> =
        flow {
            challengeClient.updateChallengeLike(
                id = id,
                isLiked = isLiked,
            )

            emit(Unit)
        }.flowOn(ioDispatcher)

    override fun getChallengeParticipationFeed(
        challengeId: Long,
        cursor: String?,
        size: Int,
    ): Flow<CursorPage<ChallengeParticipation>> =
        flow {
            val response = challengeClient.getParticipationVideoFeed(
                challengeId = challengeId,
                cursor = cursor,
                size = size,
            )

            emit(response.toModel())
        }.flowOn(ioDispatcher)

    override fun getChallengeParticipation(
        id: Long,
    ): Flow<ChallengeParticipation> =
        flow {
            val response = challengeClient.getParticipationVideo(
                id = id,
            )

            emit(response.toModel())
        }.flowOn(ioDispatcher)

    override fun publishChallengeParticipation(
        jobUuid: String,
        description: String,
        visibility: ParticipationVisibility,
        commentEnabled: Boolean,
    ): Flow<Long> =
        flow {
            val response = challengeClient.publishParticipationVideo(
                jobUuid = jobUuid,
                description = description,
                visibility = visibility.toNetwork(),
                commentEnabled = commentEnabled,
            )

            emit(response.videoSeq)
        }.flowOn(ioDispatcher)

    override fun updateChallengeParticipation(
        id: Long,
        description: String?,
        visibility: ParticipationVisibility?,
        commentEnabled: Boolean?,
    ): Flow<ChallengeParticipation> =
        flow {
            val response = challengeClient.updateParticipationVideo(
                id = id,
                description = description,
                visibility = visibility?.toNetwork(),
                commentEnabled = commentEnabled,
            )

            emit(response.toModel())
        }.flowOn(ioDispatcher)

    override fun deleteChallengeParticipation(
        id: Long,
    ): Flow<Unit> =
        flow {
            challengeClient.deleteParticipationVideo(
                id = id,
            )

            emit(Unit)
        }.flowOn(ioDispatcher)

    override fun updateChallengeParticipationLike(
        id: Long,
        isLiked: Boolean,
    ): Flow<Unit> =
        flow {
            challengeClient.updateParticipationVideoLike(
                id = id,
                isLiked = isLiked,
            )

            emit(Unit)
        }.flowOn(ioDispatcher)

    override fun hideChallengeParticipation(
        id: Long,
    ): Flow<Unit> =
        flow {
            challengeClient.hideParticipationVideo(
                id = id,
            )

            emit(Unit)
        }.flowOn(ioDispatcher)

    override fun reportChallengeParticipation(
        id: Long,
        reasonCode: String,
    ): Flow<Unit> =
        flow {
            challengeClient.reportParticipationVideo(
                id = id,
                reasonCode = reasonCode,
            )

            emit(Unit)
        }.flowOn(ioDispatcher)
}