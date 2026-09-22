package com.ssafy.motif.core.data.repository

import com.ssafy.motif.core.data.model.CursorPage
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
import java.io.File
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

    fun getChallengeCapturePositions(
        challengeId: Long,
    ): Flow<List<CapturePosition>>

    fun getChallengeCaptureVideo(
        participantId: Long,
    ): Flow<CaptureGuideVideo>

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

    fun getHotChallengeParticipations(
        cursor: String? = null,
        size: Int = DEFAULT_FEED_SIZE,
    ): Flow<CursorPage<ChallengeParticipationSummary>>

    fun createChallengeCompositeJob(
        challengeId: Long,
        challengeParticipantId: Long? = null,
        contentLength: Long,
    ): Flow<CompositeJobUpload>

    fun uploadChallengeCompositeVideo(
        uploadUrl: String,
        videoFile: File,
    ): Flow<Int>

    fun requestChallengeCompositeJob(
        jobUuid: String,
        correctionEnabled: Boolean,
    ): Flow<CompositeJobRequestResult>

    fun getChallengeCompositeJob(
        jobUuid: String,
    ): Flow<ChallengeCompositeJob>

    fun retryChallengeCompositeJob(
        jobUuid: String,
    ): Flow<CompositeJobRetryResult>

    private companion object {
        const val DEFAULT_FEED_SIZE = 20
    }
}