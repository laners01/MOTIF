package com.ssafy.motif.core.data.mapper

import com.ssafy.motif.core.data.model.CursorPage
import com.ssafy.motif.core.model.artist.Artist
import com.ssafy.motif.core.model.challenge.ParticipationVisibility
import com.ssafy.motif.core.model.user.RepresentativeMotif
import com.ssafy.motif.core.model.user.UserActivity
import com.ssafy.motif.core.model.user.UserActivityComment
import com.ssafy.motif.core.model.user.UserActivityParentComment
import com.ssafy.motif.core.model.user.UserActivityReply
import com.ssafy.motif.core.model.user.UserActivityVideo
import com.ssafy.motif.core.model.user.UserOverview
import com.ssafy.motif.core.model.user.UserProfile
import com.ssafy.motif.core.model.user.UserProfileDetail
import com.ssafy.motif.core.model.user.UserSubscription
import com.ssafy.motif.core.model.user.UserVideo
import com.ssafy.motif.core.network.model.challenge.VideoVisibility
import com.ssafy.motif.core.network.model.user.RepresentativeMotifResponse
import com.ssafy.motif.core.network.model.user.UserActivityAuthorResponse
import com.ssafy.motif.core.network.model.user.UserActivityCommentResponse
import com.ssafy.motif.core.network.model.user.UserActivityListResponse
import com.ssafy.motif.core.network.model.user.UserActivityParentCommentResponse
import com.ssafy.motif.core.network.model.user.UserActivityReplyResponse
import com.ssafy.motif.core.network.model.user.UserActivityResponse
import com.ssafy.motif.core.network.model.user.UserActivityVideoResponse
import com.ssafy.motif.core.network.model.user.UserArtistResponse
import com.ssafy.motif.core.network.model.user.UserOverviewResponse
import com.ssafy.motif.core.network.model.user.UserProfileResponse
import com.ssafy.motif.core.network.model.user.UserSubscriptionListResponse
import com.ssafy.motif.core.network.model.user.UserSubscriptionResponse
import com.ssafy.motif.core.network.model.user.UserVideoListResponse
import com.ssafy.motif.core.network.model.user.UserVideoResponse
import java.time.Instant

internal fun UserOverviewResponse.toModel(): UserOverview =
    UserOverview(
        profile =
            UserProfile(
                id = userSeq,
                nickname = nickname,
                profileImageUrl = profileImageUrl,
            ),
        representativeMotif =
            repMotif?.toModel(),
        videos =
            videos.map { response ->
                response.toModel()
            },
    )

private fun RepresentativeMotifResponse.toModel(): RepresentativeMotif =
    RepresentativeMotif(
        id = resultSeq,
        videoUrl = videoUrl,
        thumbnailUrl = thumbnailUrl,
    )

internal fun UserProfileResponse.toModel(): UserProfileDetail =
    UserProfileDetail(
        profile =
            UserProfile(
                id = userSeq,
                nickname = nickname,
                profileImageUrl = profileImageUrl,
            ),
        subscribedArtists =
            subscribedArtists.map { response ->
                response.toModel()
            },
    )

private fun UserArtistResponse.toModel(): Artist =
    Artist(
        id = artistSeq,
        name = name,
        profileImageUrl = profileImageUrl.orEmpty(),
        groupName = groupName,
    )

internal fun UserSubscriptionListResponse.toModel():
        List<UserSubscription> =
    list.map { response ->
        response.toModel()
    }

private fun UserSubscriptionResponse.toModel(): UserSubscription =
    UserSubscription(
        artist =
            Artist(
                id = artistSeq,
                name = name,
                profileImageUrl = profileImageUrl.orEmpty(),
                groupName = groupName,
            ),
        passActive = passActive,
        passExpiresAt =
            passExpiresDttm?.let { value ->
                Instant
                    .parse(value)
                    .toEpochMilli()
            },
        subscribedDays = subscribedDays,
        challengeCount = challengeCount,
        purchasedAssetCount = purchasedAssetCount,
        totalAssetCount = totalAssetCount,
    )

internal fun UserVideoListResponse.toModel(): List<UserVideo> =
    list.map { response ->
        response.toModel()
    }

private fun UserVideoResponse.toModel(): UserVideo =
    UserVideo(
        id = videoSeq,
        thumbnailUrl = thumbnailUrl,
        viewCount = viewCount,
        visibility = visibility.toModel(),
    )

private fun VideoVisibility.toModel(): ParticipationVisibility =
    when (this) {
        VideoVisibility.PUBLIC ->
            ParticipationVisibility.PUBLIC

        VideoVisibility.PRIVATE ->
            ParticipationVisibility.PRIVATE
    }

internal fun UserActivityListResponse.toModel():
        CursorPage<UserActivity> =
    CursorPage(
        items =
            list.map { response ->
                response.toModel()
            },
        previousCursor = prevCursor,
        nextCursor = nextCursor,
        hasPrevious = hasPrevious,
        hasNext = hasNext,
    )

private fun UserActivityResponse.toModel(): UserActivity =
    UserActivity(
        video = video.toModel(),
        isLiked = liked,
        myComment = myComment?.toModel(),
        replies =
            replies.map { response ->
                response.toModel()
            },
        lastInteractedAt =
            Instant
                .parse(lastInteractedDttm)
                .toEpochMilli(),
    )

private fun UserActivityVideoResponse.toModel(): UserActivityVideo =
    UserActivityVideo(
        id = videoSeq,
        thumbnailUrl = thumbnailUrl,
        viewCount = viewCount,
        likeCount = likeCount,
    )

private fun UserActivityCommentResponse.toModel():
        UserActivityComment =
    UserActivityComment(
        id = commentSeq,
        content = content,
        likeCount = likeCount,
        replyCount = replyCount,
        createdAt =
            Instant
                .parse(regDttm)
                .toEpochMilli(),
        parentComment =
            parentComment?.toModel(),
    )

private fun UserActivityParentCommentResponse.toModel():
        UserActivityParentComment =
    UserActivityParentComment(
        id = commentSeq,
        author = author.toModel(),
        content = content,
    )

private fun UserActivityReplyResponse.toModel(): UserActivityReply =
    UserActivityReply(
        id = commentSeq,
        author = author.toModel(),
        content = content,
        createdAt =
            Instant
                .parse(regDttm)
                .toEpochMilli(),
    )

private fun UserActivityAuthorResponse.toModel(): UserProfile =
    UserProfile(
        id = userSeq,
        nickname = nickname,
        profileImageUrl = profileImageUrl,
    )