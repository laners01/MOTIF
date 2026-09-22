package com.ssafy.motif.core.data.mapper

import com.ssafy.motif.core.model.artist.Artist
import com.ssafy.motif.core.model.challenge.CaptureGuideVideo
import com.ssafy.motif.core.model.challenge.CapturePosition
import com.ssafy.motif.core.network.model.challenge.ChallengeCapturePositionListResponse
import com.ssafy.motif.core.network.model.challenge.ChallengeCapturePositionResponse
import com.ssafy.motif.core.network.model.challenge.ChallengeCaptureVideoResponse

internal fun ChallengeCapturePositionListResponse.toCapturePositions():
        List<CapturePosition> =
    list
        .sortedBy { response ->
            response.slotOrder
        }
        .map { response ->
            response.toCapturePosition()
        }

private fun ChallengeCapturePositionResponse.toCapturePosition():
        CapturePosition =
    CapturePosition(
        id = challengeParticipantSeq,
        artist = Artist(
            id = artistSeq,
            name = name,
            profileImageUrl =
                profileImageUrl.orEmpty(),
            groupName = null,
        ),
        slotOrder = slotOrder,
        x = anchor?.x?.toFloat(),
        y = anchor?.y?.toFloat(),
        initialSceneUrl =
            initialSceneUrl,
        previewImageUrl =
            previewImageUrl,
    )

internal fun ChallengeCaptureVideoResponse.toCaptureGuideVideo():
        CaptureGuideVideo =
    CaptureGuideVideo(
        participantId =
            challengeParticipantSeq,
        challengeId =
            challengeSeq,
        artistId =
            artistSeq,
        videoUrl =
            videoUrl,
        durationMs =
            durationMs,
        expiresIn =
            expiresIn,
    )