package com.ssafy.motif.core.network.service.challenge

import com.ssafy.motif.core.network.model.challenge.ChallengeCapturePositionListResponse
import com.ssafy.motif.core.network.model.challenge.ChallengeCaptureVideoResponse
import com.ssafy.motif.core.network.model.challenge.ChallengeDetailResponse
import com.ssafy.motif.core.network.model.challenge.ChallengeFeedResponse
import com.ssafy.motif.core.network.model.challenge.HotChallengeParticipationFeedResponse
import com.ssafy.motif.core.network.model.challenge.ParticipationVideoFeedResponse
import com.ssafy.motif.core.network.model.challenge.ParticipationVideoResponse
import com.ssafy.motif.core.network.model.challenge.PublishParticipationVideoRequest
import com.ssafy.motif.core.network.model.challenge.PublishParticipationVideoResponse
import com.ssafy.motif.core.network.model.challenge.ReportParticipationVideoRequest
import com.ssafy.motif.core.network.model.challenge.UpdateParticipationVideoRequest
import com.ssafy.motif.core.network.model.challenge.VideoVisibility
import com.ssafy.motif.core.network.model.challenge.ViewCountRequest
import javax.inject.Inject

class ChallengeClient @Inject constructor(
    private val challengeService: ChallengeService,
) {

    suspend fun getChallengeFeed(
        cursor: String? = null,
        size: Int = DEFAULT_FEED_SIZE,
    ): ChallengeFeedResponse =
        challengeService
            .getChallengeFeed(
                cursor = cursor,
                size = size,
            )
            .data

    suspend fun getChallengeDetail(
        id: Long,
    ): ChallengeDetailResponse =
        challengeService
            .getChallengeDetail(
                challengeSeq = id,
            )
            .data

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

    suspend fun recordChallengeViews(
        views: Map<Long, Int>,
    ) {
        challengeService.postChallengeViews(
            request = views.toViewCountRequest(),
        )
    }

    suspend fun getChallengeCapturePositions(
        challengeId: Long,
    ): ChallengeCapturePositionListResponse =
        challengeService
            .getChallengeCapturePositions(
                challengeSeq = challengeId,
            )
            .data

    suspend fun getChallengeCaptureVideo(
        participantId: Long,
    ): ChallengeCaptureVideoResponse =
        challengeService
            .getChallengeCaptureVideo(
                challengeParticipantSeq =
                    participantId,
            )
            .data

    suspend fun getParticipationVideoFeed(
        challengeId: Long,
        cursor: String? = null,
        size: Int = DEFAULT_FEED_SIZE,
    ): ParticipationVideoFeedResponse =
        challengeService
            .getParticipationVideoFeed(
                challengeSeq = challengeId,
                cursor = cursor,
                size = size,
            )
            .data

    suspend fun getParticipationVideo(
        id: Long,
    ): ParticipationVideoResponse =
        challengeService
            .getParticipationVideo(
                videoSeq = id,
            )
            .data

    suspend fun recordParticipationVideoViews(
        views: Map<Long, Int>,
    ) {
        challengeService.postParticipationVideoViews(
            request = views.toViewCountRequest(),
        )
    }

    suspend fun publishParticipationVideo(
        jobUuid: String,
        description: String,
        visibility: VideoVisibility,
        commentEnabled: Boolean,
    ): PublishParticipationVideoResponse =
        challengeService
            .publishParticipationVideo(
                request =
                    PublishParticipationVideoRequest(
                        jobUuid = jobUuid,
                        description = description,
                        visibility = visibility,
                        commentEnabled =
                            commentEnabled,
                    ),
            )
            .data

    suspend fun updateParticipationVideo(
        id: Long,
        description: String? = null,
        visibility: VideoVisibility? = null,
        commentEnabled: Boolean? = null,
    ): ParticipationVideoResponse =
        challengeService
            .updateParticipationVideo(
                videoSeq = id,
                request =
                    UpdateParticipationVideoRequest(
                        description = description,
                        visibility = visibility,
                        commentEnabled =
                            commentEnabled,
                    ),
            )
            .data

    suspend fun deleteParticipationVideo(
        id: Long,
    ) {
        challengeService
            .deleteParticipationVideo(
                videoSeq = id,
            )
    }

    suspend fun updateParticipationVideoLike(
        id: Long,
        isLiked: Boolean,
    ) {
        if (isLiked) {
            challengeService
                .postParticipationVideoLike(
                    videoSeq = id,
                )
        } else {
            challengeService
                .deleteParticipationVideoLike(
                    videoSeq = id,
                )
        }
    }

    suspend fun hideParticipationVideo(
        id: Long,
    ) {
        challengeService
            .hideParticipationVideo(
                videoSeq = id,
            )
    }

    suspend fun reportParticipationVideo(
        id: Long,
        reasonCode: String,
    ) {
        challengeService
            .reportParticipationVideo(
                videoSeq = id,
                request =
                    ReportParticipationVideoRequest(
                        reasonCode = reasonCode,
                    ),
            )
    }

    suspend fun getHotChallengeParticipations(
        cursor: String? = null,
        size: Int = DEFAULT_FEED_SIZE,
    ): HotChallengeParticipationFeedResponse =
        challengeService
            .getHotChallengeParticipations(
                cursor = cursor,
                size = size,
            )
            .data

    private fun Map<Long, Int>.toViewCountRequest(): ViewCountRequest {
        require(size in 1..MAX_VIEW_TARGETS) {
            "views size must be between 1 and $MAX_VIEW_TARGETS"
        }

        forEach { (id, count) ->
            require(id > 0L) {
                "view target id must be greater than 0"
            }

            require(count in 1..MAX_VIEW_COUNT_PER_TARGET) {
                "view count must be between 1 and $MAX_VIEW_COUNT_PER_TARGET"
            }
        }

        return ViewCountRequest(
            views =
                mapKeys { (id, _) ->
                    id.toString()
                },
        )
    }

    private companion object {
        const val DEFAULT_FEED_SIZE = 20

        const val MAX_VIEW_TARGETS = 50

        const val MAX_VIEW_COUNT_PER_TARGET = 100
    }
}