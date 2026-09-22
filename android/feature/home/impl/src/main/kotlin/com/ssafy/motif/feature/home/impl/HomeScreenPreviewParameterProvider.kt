package com.ssafy.motif.feature.home.impl

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.ssafy.motif.core.model.artist.Artist
import com.ssafy.motif.core.model.artist.RankedArtist
import com.ssafy.motif.core.model.challenge.ChallengeParticipationSummary
import com.ssafy.motif.core.model.challenge.ChallengeSong
import com.ssafy.motif.core.model.comment.Comment
import com.ssafy.motif.core.model.comment.CommentAuthorType
import com.ssafy.motif.core.model.motif.Motif
import com.ssafy.motif.core.model.notice.Notice
import com.ssafy.motif.core.ui.comment.CommentPage
import com.ssafy.motif.core.ui.comment.CommentUiState
import com.ssafy.motif.feature.home.impl.model.FanRadarUiState
import com.ssafy.motif.feature.home.impl.model.FeedAppendUiState
import com.ssafy.motif.feature.home.impl.model.MyArtistFeedItem
import com.ssafy.motif.feature.home.impl.model.MyArtistFeedUiState
import com.ssafy.motif.feature.home.impl.model.OnstageUiState

internal data class HomeScreenPreviewData(
    val name: String,
    val onstageUiState: OnstageUiState,
    val fanRadarUiState: FanRadarUiState,
    val myArtistFeedUiState: MyArtistFeedUiState,
    val commentUiState: CommentUiState = CommentUiState(),
) {
    override fun toString(): String = name
}

internal class HomeScreenPreviewParameterProvider :
    PreviewParameterProvider<HomeScreenPreviewData> {

    override val values: Sequence<HomeScreenPreviewData> =
        sequenceOf(
            HomeScreenPreviewData(
                name = "Success",
                onstageUiState = previewOnstageUiState,
                fanRadarUiState = previewFanRadarUiState,
                myArtistFeedUiState = previewMyArtistFeedUiState,
            ),
            HomeScreenPreviewData(
                name = "Loading",
                onstageUiState = OnstageUiState.Loading,
                fanRadarUiState = FanRadarUiState.Loading,
                myArtistFeedUiState = MyArtistFeedUiState.Loading,
            ),
            HomeScreenPreviewData(
                name = "Error",
                onstageUiState = OnstageUiState.Error(
                    exception = IllegalStateException(
                        "인기 챌린지를 불러오지 못했습니다.",
                    ),
                ),
                fanRadarUiState = FanRadarUiState.Error(
                    exception = IllegalStateException(
                        "팬 레이더를 불러오지 못했습니다.",
                    ),
                ),
                myArtistFeedUiState = MyArtistFeedUiState.Error(
                    exception = IllegalStateException(
                        "피드를 불러오지 못했습니다.",
                    ),
                ),
            ),
            HomeScreenPreviewData(
                name = "Feed Loading More",
                onstageUiState = previewOnstageUiState,
                fanRadarUiState = previewFanRadarUiState,
                myArtistFeedUiState = MyArtistFeedUiState.Success(
                    items = previewMyArtistFeedItems,
                    hasNext = true,
                    appendState = FeedAppendUiState.Loading,
                ),
            ),
            HomeScreenPreviewData(
                name = "Comment Sheet",
                onstageUiState = previewOnstageUiState,
                fanRadarUiState = previewFanRadarUiState,
                myArtistFeedUiState = previewMyArtistFeedUiState,
                commentUiState = previewCommentUiState,
            ),
        )
}

private val previewArtist =
    Artist(
        id = 1L,
        name = "YEJI",
        profileImageUrl = "https://picsum.photos/seed/yeji/200",
        groupName = "ITZY",
    )

private val previewChallengeParticipationSummaryItems =
    listOf(
        ChallengeParticipationSummary(
            id = 4L,
            rank = 1,
            videoUrl = "https://d21so2cpi8si9r.cloudfront.net/challenges/d7f49b37-54e7-4e2f-888e-3d24f6f453d4/master.m3u8",
            thumbnailUrl = "https://d21so2cpi8si9r.cloudfront.net/challenges/d7f49b37-54e7-4e2f-888e-3d24f6f453d4/thumb.jpg",
            song = ChallengeSong(
                title = "like JENNY",
                artistName = "JENNIE",
            ),
        ),
        ChallengeParticipationSummary(
            id = 3L,
            rank = 2,
            videoUrl = "https://d21so2cpi8si9r.cloudfront.net/challenges/5ece3915-c32b-4836-8b55-868e49a8d8f0/master.m3u8",
            thumbnailUrl = "https://d21so2cpi8si9r.cloudfront.net/challenges/5ece3915-c32b-4836-8b55-868e49a8d8f0/thumb.jpg",
            song = ChallengeSong(
                title = "바라밤",
                artistName = "뽀로로",
            ),
        ),
        ChallengeParticipationSummary(
            id = 2L,
            rank = 3,
            videoUrl = "https://d21so2cpi8si9r.cloudfront.net/challenges/a0006c4a-2746-4bf9-8bd1-bafa6e123b55/master.m3u8",
            thumbnailUrl = "https://d21so2cpi8si9r.cloudfront.net/challenges/a0006c4a-2746-4bf9-8bd1-bafa6e123b55/thumb.jpg",
            song = ChallengeSong(
                title = "See Tinh",
                artistName = "Hoang Thuy Linh",
            ),
        ),
        ChallengeParticipationSummary(
            id = 1L,
            rank = 4,
            videoUrl = "https://d21so2cpi8si9r.cloudfront.net/challenges/7dfa4de1-792a-41ec-a438-f51c48c15b1e/master.m3u8",
            thumbnailUrl = "https://d21so2cpi8si9r.cloudfront.net/challenges/7dfa4de1-792a-41ec-a438-f51c48c15b1e/thumb.jpg",
            song = ChallengeSong(
                title = "Break Your Legs",
                artistName = "Shams",
            ),
        ),
    )

private val previewRankedArtists =
    listOf(
        RankedArtist(
            artist = Artist(
                id = 1L,
                name = "카리나",
                profileImageUrl = "https://picsum.photos/seed/karina/200",
                groupName = "aespa",
            ),
            rank = 1,
            rankDelta = 0,
            isSubscribed = true,
        ),
        RankedArtist(
            artist = Artist(
                id = 2L,
                name = "지수",
                profileImageUrl = "https://picsum.photos/seed/jisoo/200",
                groupName = "BLACKPINK",
            ),
            rank = 2,
            rankDelta = 1,
            isSubscribed = false,
        ),
        RankedArtist(
            artist = Artist(
                id = 3L,
                name = "이채영",
                profileImageUrl = "https://picsum.photos/seed/chaeyoung/200",
                groupName = "프로미스나인",
            ),
            rank = 3,
            rankDelta = -1,
            isSubscribed = true,
        ),
        RankedArtist(
            artist = Artist(
                id = 4L,
                name = "오해원",
                profileImageUrl = "https://picsum.photos/seed/haewon/200",
                groupName = "NMIXX",
            ),
            rank = 4,
            rankDelta = null,
            isSubscribed = false,
        ),
    )

private val previewNotice1 =
    Notice(
        id = 1L,
        artist = previewArtist,
        content = "안녕하세요. 예지입니다. 오늘도 좋은 하루 보내고 계신가요? 곧 새로운 모습으로 만나요 💛",
        imageUrls = listOf(
            "https://picsum.photos/seed/notice1/800/800",
            "https://picsum.photos/seed/notice2/800/800",
        ),
        likeCount = 2_100L,
        commentCount = 1_100L,
        isLiked = false,
        createdAt = 4L,
    )

private val previewNotice2 =
    Notice(
        id = 2L,
        artist = previewArtist,
        content = "오늘도 함께해줘서 고마워요!",
        imageUrls = emptyList(),
        likeCount = 842L,
        commentCount = 123L,
        isLiked = true,
        createdAt = 2L,
    )

private val previewMotif1 =
    Motif(
        id = 1L,
        thumbnailUrl = "https://picsum.photos/seed/motif1/1200/800",
        title = "카페 맞은편 자리에 앉아 예지와 함께 여유로운 하루",
        artist = previewArtist,
        createdAt = 3L,
        description = "",
        videoUrl = "",
    )

private val previewMotif2 =
    Motif(
        id = 2L,
        thumbnailUrl = "https://picsum.photos/seed/motif2/1200/800",
        title = "예지와 함께 보내는 특별한 순간",
        artist = previewArtist,
        createdAt = 1L,
        description = "",
        videoUrl = "",
    )

private val previewMyArtistFeedItems =
    listOf(
        MyArtistFeedItem.NoticeItem(
            notice = previewNotice1,
        ),
        MyArtistFeedItem.MotifItem(
            motif = previewMotif1,
        ),
        MyArtistFeedItem.NoticeItem(
            notice = previewNotice2,
        ),
        MyArtistFeedItem.MotifItem(
            motif = previewMotif2,
        ),
    )

private val previewComments =
    listOf(
        Comment(
            id = 1L,
            profileImageUrl = "https://picsum.photos/seed/comment1/200",
            nickname = "YEJI",
            content = "다들 너무 잘 따라 해줘서 고마워요 🖤",
            createdAt = System.currentTimeMillis() - 30 * 60_000L,
            likeCount = 2_841L,
            replyCount = 32L,
            isLiked = true,
            authorType = CommentAuthorType.ARTIST,
        ),
        Comment(
            id = 2L,
            profileImageUrl = "https://picsum.photos/seed/comment2/200",
            nickname = "motif_me",
            content = "이번 챌린지 진짜 재밌었어요!",
            createdAt = System.currentTimeMillis() - 2 * 60 * 60_000L,
            likeCount = 124L,
            replyCount = 3L,
            isLiked = false,
            authorType = CommentAuthorType.CURRENT_USER,
        ),
        Comment(
            id = 3L,
            profileImageUrl = "https://picsum.photos/seed/comment3/200",
            nickname = "midzy_01",
            content = "다음 챌린지도 기다리고 있을게요 👀",
            createdAt = System.currentTimeMillis() - 5 * 60 * 60_000L,
            likeCount = 48L,
            replyCount = 1L,
            isLiked = false,
            authorType = CommentAuthorType.USER,
        ),
        Comment(
            id = 4L,
            profileImageUrl = "https://picsum.photos/seed/comment4/200",
            nickname = "always_itzy",
            content = "마지막 포인트 안무가 제일 어렵네요 ㅋㅋ",
            createdAt = System.currentTimeMillis() - 24 * 60 * 60_000L,
            likeCount = 21L,
            replyCount = 0L,
            isLiked = true,
            authorType = CommentAuthorType.USER,
        ),
    )

private val previewOnstageUiState =
    OnstageUiState.Success(
        items = previewChallengeParticipationSummaryItems,
    )

private val previewFanRadarUiState =
    FanRadarUiState.Success(
        artists = previewRankedArtists,
    )

private val previewMyArtistFeedUiState =
    MyArtistFeedUiState.Success(
        items = previewMyArtistFeedItems,
        hasNext = true,
    )

private val previewCommentUiState =
    CommentUiState(
        page = CommentPage.Comments,
        comments = previewComments,
    )