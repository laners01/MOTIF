package com.ssafy.motif.core.data.repository

import com.ssafy.motif.core.common.network.Dispatcher
import com.ssafy.motif.core.common.network.MotifDispatcher
import com.ssafy.motif.core.data.mapper.toArtists
import com.ssafy.motif.core.data.mapper.toCaptureGuideVideo
import com.ssafy.motif.core.data.mapper.toCapturePositions
import com.ssafy.motif.core.data.mapper.toChallengeModel
import com.ssafy.motif.core.data.mapper.toModel
import com.ssafy.motif.core.data.mapper.toNetwork
import com.ssafy.motif.core.data.model.CursorPage
import com.ssafy.motif.core.model.artist.Artist
import com.ssafy.motif.core.model.challenge.CaptureGuideVideo
import com.ssafy.motif.core.model.challenge.CapturePosition
import com.ssafy.motif.core.model.challenge.Challenge
import com.ssafy.motif.core.model.challenge.ChallengeDetail
import com.ssafy.motif.core.model.challenge.ChallengeParticipation
import com.ssafy.motif.core.model.challenge.ChallengeParticipationSummary
import com.ssafy.motif.core.model.challenge.ParticipationVisibility
import com.ssafy.motif.core.model.composite.ChallengeCompositeJob
import com.ssafy.motif.core.model.composite.CompositeJobRequestResult
import com.ssafy.motif.core.model.composite.CompositeJobRetryResult
import com.ssafy.motif.core.model.composite.CompositeJobUpload
import com.ssafy.motif.core.network.service.challenge.ChallengeClient
import com.ssafy.motif.core.network.service.composite.CompositeJobClient
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import javax.inject.Inject

class ChallengeRepositoryImpl @Inject constructor(
    private val challengeClient: ChallengeClient,
    private val compositeJobClient: CompositeJobClient,
    @param:Dispatcher(MotifDispatcher.IO)
    private val ioDispatcher: CoroutineDispatcher,
) : ChallengeRepository {

    override fun getChallengeFeed(
        cursor: String?,
        size: Int,
    ): Flow<CursorPage<Challenge>> =
        flow {
            val response =
                challengeClient.getChallengeFeed(
                    cursor = cursor,
                    size = size,
                )

            emit(
                response.toModel(),
            )
        }.flowOn(ioDispatcher)

    override fun getChallengeDetail(
        id: Long,
    ): Flow<ChallengeDetail> =
        flow {
            val response =
                challengeClient.getChallengeDetail(
                    id = id,
                )

            emit(
                response.toModel(),
            )
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

    override fun getChallengeCapturePositions(
        challengeId: Long,
    ): Flow<List<CapturePosition>> =
        flow {
            val response =
                challengeClient
                    .getChallengeCapturePositions(
                        challengeId =
                            challengeId,
                    )

            emit(
                response.toCapturePositions(),
            )
        }.flowOn(ioDispatcher)

    override fun getChallengeArtists(
        challengeId: Long,
    ): Flow<List<Artist>> =
        flow {
            val response =
                challengeClient
                    .getChallengeCapturePositions(
                        challengeId =
                            challengeId,
                    )

            emit(
                response.toArtists(),
            )
        }.flowOn(ioDispatcher)

    override fun getChallengeCaptureVideo(
        participantId: Long,
    ): Flow<CaptureGuideVideo> =
        flow {
            val response =
                challengeClient
                    .getChallengeCaptureVideo(
                        participantId =
                            participantId,
                    )

            emit(
                response.toCaptureGuideVideo(),
            )
        }.flowOn(ioDispatcher)

    override fun getChallengeParticipationFeed(
        challengeId: Long,
        cursor: String?,
        size: Int,
    ): Flow<CursorPage<ChallengeParticipation>> =
        flow {
            val response =
                challengeClient
                    .getParticipationVideoFeed(
                        challengeId =
                            challengeId,
                        cursor = cursor,
                        size = size,
                    )

            emit(
                response.toModel(),
            )
        }.flowOn(ioDispatcher)

    override fun getChallengeParticipation(
        id: Long,
    ): Flow<ChallengeParticipation> =
        flow {
            val response =
                challengeClient
                    .getParticipationVideo(
                        id = id,
                    )

            emit(
                response.toModel(),
            )
        }.flowOn(ioDispatcher)

    override fun publishChallengeParticipation(
        jobUuid: String,
        description: String,
        visibility: ParticipationVisibility,
        commentEnabled: Boolean,
    ): Flow<Long> =
        flow {
            val response =
                challengeClient
                    .publishParticipationVideo(
                        jobUuid = jobUuid,
                        description = description,
                        visibility =
                            visibility.toNetwork(),
                        commentEnabled =
                            commentEnabled,
                    )

            emit(
                response.videoSeq,
            )
        }.flowOn(ioDispatcher)

    override fun updateChallengeParticipation(
        id: Long,
        description: String?,
        visibility: ParticipationVisibility?,
        commentEnabled: Boolean?,
    ): Flow<ChallengeParticipation> =
        flow {
            val response =
                challengeClient
                    .updateParticipationVideo(
                        id = id,
                        description = description,
                        visibility =
                            visibility?.toNetwork(),
                        commentEnabled =
                            commentEnabled,
                    )

            emit(
                response.toModel(),
            )
        }.flowOn(ioDispatcher)

    override fun deleteChallengeParticipation(
        id: Long,
    ): Flow<Unit> =
        flow {
            challengeClient
                .deleteParticipationVideo(
                    id = id,
                )

            emit(Unit)
        }.flowOn(ioDispatcher)

    override fun updateChallengeParticipationLike(
        id: Long,
        isLiked: Boolean,
    ): Flow<Unit> =
        flow {
            challengeClient
                .updateParticipationVideoLike(
                    id = id,
                    isLiked = isLiked,
                )

            emit(Unit)
        }.flowOn(ioDispatcher)

    override fun hideChallengeParticipation(
        id: Long,
    ): Flow<Unit> =
        flow {
            challengeClient
                .hideParticipationVideo(
                    id = id,
                )

            emit(Unit)
        }.flowOn(ioDispatcher)

    override fun reportChallengeParticipation(
        id: Long,
        reasonCode: String,
    ): Flow<Unit> =
        flow {
            challengeClient
                .reportParticipationVideo(
                    id = id,
                    reasonCode = reasonCode,
                )

            emit(Unit)
        }.flowOn(ioDispatcher)

    override fun getHotChallengeParticipations(
        cursor: String?,
        size: Int,
    ): Flow<CursorPage<ChallengeParticipationSummary>> =
        flow {
            val response =
                challengeClient
                    .getHotChallengeParticipations(
                        cursor = cursor,
                        size = size,
                    )

            emit(
                response.toModel(),
            )
        }.flowOn(ioDispatcher)

    override fun createChallengeCompositeJob(
        challengeId: Long,
        challengeParticipantId: Long?,
        contentLength: Long,
    ): Flow<CompositeJobUpload> =
        flow {
            val response =
                compositeJobClient
                    .createChallengeJob(
                        challengeId =
                            challengeId,
                        challengeParticipantId =
                            challengeParticipantId,
                        contentLength =
                            contentLength,
                    )

            emit(
                response.toModel(),
            )
        }.flowOn(ioDispatcher)

    override fun uploadChallengeCompositeVideo(
        uploadUrl: String,
        videoFile: File,
    ): Flow<Int> =
        channelFlow {
            trySend(
                INITIAL_UPLOAD_PROGRESS,
            )

            compositeJobClient.uploadVideo(
                uploadUrl =
                    uploadUrl,
                videoFile =
                    videoFile,
                onProgress = { progress ->
                    trySend(
                        progress,
                    )
                },
            )
        }
            .distinctUntilChanged()
            .flowOn(
                ioDispatcher,
            )

    override fun requestChallengeCompositeJob(
        jobUuid: String,
        correctionEnabled: Boolean,
    ): Flow<CompositeJobRequestResult> =
        flow {
            val response =
                compositeJobClient
                    .requestChallengeJob(
                        jobUuid = jobUuid,
                        correctionEnabled =
                            correctionEnabled,
                    )

            emit(
                response.toModel(),
            )
        }.flowOn(ioDispatcher)

    override fun getChallengeCompositeJob(
        jobUuid: String,
    ): Flow<ChallengeCompositeJob> =
        flow {
            val response =
                compositeJobClient
                    .getCompositeJob(
                        jobUuid = jobUuid,
                    )

            emit(
                response.toChallengeModel(),
            )
        }.flowOn(ioDispatcher)

    override fun retryChallengeCompositeJob(
        jobUuid: String,
    ): Flow<CompositeJobRetryResult> =
        flow {
            val response =
                compositeJobClient
                    .retryCompositeJob(
                        jobUuid = jobUuid,
                    )

            emit(
                response.toModel(),
            )
        }.flowOn(ioDispatcher)

    override fun recordChallengeViews(
        views: Map<Long, Int>,
    ): Flow<Unit> =
        flow {
            challengeClient.recordChallengeViews(
                views = views,
            )

            emit(Unit)
        }.flowOn(ioDispatcher)

    override fun recordChallengeParticipationViews(
        views: Map<Long, Int>,
    ): Flow<Unit> =
        flow {
            challengeClient.recordParticipationVideoViews(
                views = views,
            )

            emit(Unit)
        }.flowOn(ioDispatcher)

    private companion object {
        const val INITIAL_UPLOAD_PROGRESS = 0
    }
}