package com.ssafy.motif.core.data.mapper

import com.ssafy.motif.core.data.model.CursorPage
import com.ssafy.motif.core.model.artist.Artist
import com.ssafy.motif.core.model.challenge.Challenge
import com.ssafy.motif.core.model.challenge.ChallengeAnchor
import com.ssafy.motif.core.model.challenge.ChallengeDetail
import com.ssafy.motif.core.model.challenge.ChallengeParticipant
import com.ssafy.motif.core.model.challenge.ChallengeParticipation
import com.ssafy.motif.core.model.challenge.ChallengeParticipationSummary
import com.ssafy.motif.core.model.challenge.ChallengeSong
import com.ssafy.motif.core.model.challenge.EditedChallenge
import com.ssafy.motif.core.model.challenge.ParticipationVisibility
import com.ssafy.motif.core.model.user.UserProfile
import com.ssafy.motif.core.network.model.challenge.ChallengeAnchorResponse
import com.ssafy.motif.core.network.model.challenge.ChallengeArtistResponse
import com.ssafy.motif.core.network.model.challenge.ChallengeDetailResponse
import com.ssafy.motif.core.network.model.challenge.ChallengeFeedItemResponse
import com.ssafy.motif.core.network.model.challenge.ChallengeFeedResponse
import com.ssafy.motif.core.network.model.challenge.ChallengeParticipantResponse
import com.ssafy.motif.core.network.model.challenge.ChallengeSongResponse
import com.ssafy.motif.core.network.model.challenge.EditedChallengeResponse
import com.ssafy.motif.core.network.model.challenge.HotChallengeParticipationFeedResponse
import com.ssafy.motif.core.network.model.challenge.HotChallengeParticipationResponse
import com.ssafy.motif.core.network.model.challenge.ParticipationVideoAuthorResponse
import com.ssafy.motif.core.network.model.challenge.ParticipationVideoFeedResponse
import com.ssafy.motif.core.network.model.challenge.ParticipationVideoResponse
import com.ssafy.motif.core.network.model.challenge.VideoVisibility
import java.time.Instant

internal fun ChallengeFeedResponse.toModel(): CursorPage<Challenge> =
    CursorPage(
        items = list.map { response ->
            response.toModel()
        },
        previousCursor = prevCursor,
        nextCursor = nextCursor,
        hasPrevious = hasPrevious,
        hasNext = hasNext,
    )

private fun ChallengeFeedItemResponse.toModel(): Challenge =
    Challenge(
        id = challengeSeq,
        videoUrl = videoUrl,
        thumbnailUrl = thumbnailUrl,
        artist = artist.toModel(),
        description = description,
        song = song.toModel(),
        likeCount = likeCount,
        commentCount = commentCount,
        participantCount = participantCount,
        viewCount = viewCount,
        createdAt = 0L,
        isLiked = isLiked,
        isSubscribed = isSubscribed,
        challengeArtists = emptyList(),
    )

internal fun ChallengeDetailResponse.toModel(): ChallengeDetail {
    val challengeArtists = participants.map { participant ->
        participant.toArtistModel()
    }

    return ChallengeDetail(
        challenge = Challenge(
            id = challengeSeq,
            videoUrl = videoUrl,
            thumbnailUrl = thumbnailUrl,
            artist = artist.toModel(),
            description = description,
            song = song.toModel(),
            likeCount = likeCount,
            commentCount = commentCount,
            participantCount = participantCount,
            viewCount = viewCount,
            createdAt = 0L,
            isLiked = isLiked,
            isSubscribed = isSubscribed,
            challengeArtists = challengeArtists,
        ),
        description = description,
        participants = participants.map { participant ->
            participant.toModel()
        },
        editedChallenges = editedChallenges.map { editedChallenge ->
            editedChallenge.toModel()
        },
    )
}

private fun ChallengeArtistResponse.toModel(): Artist =
    Artist(
        id = artistSeq,
        name = name,
        profileImageUrl = profileImageUrl.orEmpty(),
        groupName = null,
    )

private fun ChallengeSongResponse.toModel(): ChallengeSong =
    ChallengeSong(
        title = title,
        artistName = singerName,
    )

private fun ChallengeParticipantResponse.toModel(): ChallengeParticipant =
    ChallengeParticipant(
        artist = toArtistModel(),
        participantMask = participantMask,
        anchor = anchor?.toModel(),
    )

private fun ChallengeParticipantResponse.toArtistModel(): Artist =
    Artist(
        id = artistSeq,
        name = name,
        profileImageUrl = profileImageUrl.orEmpty(),
        groupName = null,
    )

private fun ChallengeAnchorResponse.toModel(): ChallengeAnchor =
    ChallengeAnchor(
        x = x.toFloat(),
        y = y.toFloat(),
        radius = r.toFloat(),
    )

private fun EditedChallengeResponse.toModel(): EditedChallenge =
    EditedChallenge(
        participantMask = participantMask,
        previewImageUrl = previewImageUrl,
    )

internal fun ParticipationVideoFeedResponse.toModel():
        CursorPage<ChallengeParticipation> =
    CursorPage(
        items = list.map { response ->
            response.toModel()
        },
        previousCursor = prevCursor,
        nextCursor = nextCursor,
        hasPrevious = hasPrevious,
        hasNext = hasNext,
    )

internal fun ParticipationVideoResponse.toModel(): ChallengeParticipation =
    ChallengeParticipation(
        id = videoSeq,
        challengeId = challengeSeq,
        description = description,
        videoUrl = videoUrl,
        thumbnailUrl = thumbnailUrl,
        durationMs = durationMs,
        author = author.toModel(),
        viewCount = viewCount,
        likeCount = likeCount,
        commentCount = commentCount,
        hasArtistLike = hasArtistLike,
        hasArtistComment = hasArtistComment,
        isLiked = isLiked,
        commentEnabled = commentEnabled,
        visibility = visibility.toModel(),
        isMine = isMine,
        createdAt = regDttm.toEpochMilli(),
    )

private fun ParticipationVideoAuthorResponse.toModel(): UserProfile =
    UserProfile(
        id = userSeq,
        nickname = nickname,
        profileImageUrl = profileImageUrl,
    )

private fun VideoVisibility.toModel(): ParticipationVisibility =
    when (this) {
        VideoVisibility.PUBLIC ->
            ParticipationVisibility.PUBLIC

        VideoVisibility.PRIVATE ->
            ParticipationVisibility.PRIVATE
    }

internal fun ParticipationVisibility.toNetwork(): VideoVisibility =
    when (this) {
        ParticipationVisibility.PUBLIC ->
            VideoVisibility.PUBLIC

        ParticipationVisibility.PRIVATE ->
            VideoVisibility.PRIVATE
    }

internal fun HotChallengeParticipationFeedResponse.toModel():
        CursorPage<ChallengeParticipationSummary> =
    CursorPage(
        items = list.map { response ->
            response.toModel()
        },
        previousCursor = prevCursor,
        nextCursor = nextCursor,
        hasPrevious = hasPrevious,
        hasNext = hasNext,
    )

private fun HotChallengeParticipationResponse.toModel():
        ChallengeParticipationSummary =
    ChallengeParticipationSummary(
        id = videoSeq,
        rank = rank,
        videoUrl = videoUrl,
        thumbnailUrl = thumbnailUrl,
        song = song.toModel(),
    )

private fun String.toEpochMilli(): Long =
    runCatching {
        Instant.parse(this).toEpochMilli()
    }.getOrDefault(0L)