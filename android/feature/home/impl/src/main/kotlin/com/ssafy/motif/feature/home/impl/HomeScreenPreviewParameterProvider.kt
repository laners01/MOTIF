package com.ssafy.motif.feature.home.impl

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.ssafy.motif.core.model.comment.Comment
import com.ssafy.motif.core.model.comment.CommentAuthorType
import com.ssafy.motif.core.model.home.Artist
import com.ssafy.motif.core.model.home.FanRadarArtist
import com.ssafy.motif.core.model.home.Motif
import com.ssafy.motif.core.model.home.Notice
import com.ssafy.motif.core.model.home.Onstage
import com.ssafy.motif.feature.home.impl.model.CommentSheetUiState
import com.ssafy.motif.feature.home.impl.model.FanRadarUiState
import com.ssafy.motif.feature.home.impl.model.FeedAppendUiState
import com.ssafy.motif.feature.home.impl.model.MyArtistFeedUiState
import com.ssafy.motif.feature.home.impl.model.OnstageUiState
import com.ssafy.motif.feature.home.impl.model.mergeMyArtistFeedItems

internal data class HomeScreenPreviewData(
    val name: String,
    val onstageUiState: OnstageUiState,
    val fanRadarUiState: FanRadarUiState,
    val myArtistFeedUiState: MyArtistFeedUiState,
    val commentSheetUiState: CommentSheetUiState,
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
                commentSheetUiState = CommentSheetUiState.Hidden,
            ),
            HomeScreenPreviewData(
                name = "Loading",
                onstageUiState = OnstageUiState.Loading,
                fanRadarUiState = FanRadarUiState.Loading,
                myArtistFeedUiState = MyArtistFeedUiState.Loading,
                commentSheetUiState = CommentSheetUiState.Hidden,
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
                commentSheetUiState = CommentSheetUiState.Hidden,
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
                commentSheetUiState = CommentSheetUiState.Hidden,
            ),
            HomeScreenPreviewData(
                name = "Comment Sheet",
                onstageUiState = previewOnstageUiState,
                fanRadarUiState = previewFanRadarUiState,
                myArtistFeedUiState = previewMyArtistFeedUiState,
                commentSheetUiState = previewCommentSheetUiState,
            ),
        )
}

private val previewArtist = Artist(
    id = 1L,
    name = "YEJI",
    profileImageUrl = "https://picsum.photos/seed/yeji/200",
    groupName = "ITZY",
)

private val previewOnstageItems = listOf(
    Onstage(
        videoId = 4L,
        rank = 1,
        videoUrl = "https://d21so2cpi8si9r.cloudfront.net/challenges/d7f49b37-54e7-4e2f-888e-3d24f6f453d4/master.m3u8",
        thumbnailUrl = "https://d21so2cpi8si9r.cloudfront.net/challenges/d7f49b37-54e7-4e2f-888e-3d24f6f453d4/thumb.jpg",
        songArtistName = "JENNIE",
        songTitle = "like JENNY",
    ),
    Onstage(
        videoId = 3L,
        rank = 2,
        videoUrl = "https://d21so2cpi8si9r.cloudfront.net/challenges/5ece3915-c32b-4836-8b55-868e49a8d8f0/master.m3u8",
        thumbnailUrl = "https://d21so2cpi8si9r.cloudfront.net/challenges/5ece3915-c32b-4836-8b55-868e49a8d8f0/thumb.jpg",
        songArtistName = "뽀로로",
        songTitle = "바라밤",
    ),
    Onstage(
        videoId = 2L,
        rank = 3,
        videoUrl = "https://d21so2cpi8si9r.cloudfront.net/challenges/a0006c4a-2746-4bf9-8bd1-bafa6e123b55/master.m3u8",
        thumbnailUrl = "https://d21so2cpi8si9r.cloudfront.net/challenges/a0006c4a-2746-4bf9-8bd1-bafa6e123b55/thumb.jpg",
        songArtistName = "See Tinh",
        songTitle = "Hoang Thuy Linh",
    ),
    Onstage(
        videoId = 1L,
        rank = 4,
        videoUrl = "https://d21so2cpi8si9r.cloudfront.net/challenges/7dfa4de1-792a-41ec-a438-f51c48c15b1e/master.m3u8",
        thumbnailUrl = "https://d21so2cpi8si9r.cloudfront.net/challenges/7dfa4de1-792a-41ec-a438-f51c48c15b1e/thumb.jpg",
        songArtistName = "Shams",
        songTitle = "Break Your Legs",
    ),
)

private val previewFanRadarArtists = listOf(
    FanRadarArtist(
        rank = 1,
        name = "카리나",
        profileImageUrl = "https://picsum.photos/seed/karina/200",
    ),
    FanRadarArtist(
        rank = 2,
        name = "지수",
        profileImageUrl = "https://picsum.photos/seed/jisoo/200",
    ),
    FanRadarArtist(
        rank = 3,
        name = "이채영",
        profileImageUrl = "https://picsum.photos/seed/chaeyoung/200",
    ),
    FanRadarArtist(
        rank = 4,
        name = "오해원",
        profileImageUrl = "https://picsum.photos/seed/haewon/200",
    ),
)

private val previewNotices = listOf(
    Notice(
        id = 1L,
        artist = previewArtist,
        content = """
            안녕하세요. 예지입니다.
            오늘도 좋은 하루 보내고 계신가요?
            곧 새로운 모습으로 만나요 💛
        """.trimIndent(),
        imageUrls = listOf(
            "https://picsum.photos/seed/notice1/800/800",
            "https://picsum.photos/seed/notice2/800/800",
        ),
        likeCount = 2_100L,
        commentCount = 1_100L,
        isLiked = false,
        createdAt = 4L,
    ),
    Notice(
        id = 2L,
        artist = previewArtist,
        content = "오늘도 함께해줘서 고마워요!",
        imageUrls = emptyList(),
        likeCount = 842L,
        commentCount = 123L,
        isLiked = true,
        createdAt = 2L,
    ),
)

private val previewMotifs = listOf(
    Motif(
        id = 1L,
        thumbnailUrl = "https://picsum.photos/seed/motif1/1200/800",
        title = "카페 맞은편 자리에 앉아 예지와 함께 여유로운 하루",
        artist = previewArtist,
        createdAt = 3L,
    ),
    Motif(
        id = 2L,
        thumbnailUrl = "https://picsum.photos/seed/motif2/1200/800",
        title = "예지와 함께 보내는 특별한 순간",
        artist = previewArtist,
        createdAt = 1L,
    ),
)

private val previewMyArtistFeedItems =
    mergeMyArtistFeedItems(
        notices = previewNotices,
        motifs = previewMotifs,
    )

private val previewComments = listOf(
    Comment(
        id = 1L,
        profileImageUrl = "https://picsum.photos/seed/comment1/200",
        nickname = "yeji_lover",
        content = "이번 사진 너무 좋아요 💛",
        createdAt = 0L,
        likeCount = 742L,
        replyCount = 2L,
        isLiked = false,
        authorType = CommentAuthorType.USER,
    ),
    Comment(
        id = 2L,
        profileImageUrl = "https://picsum.photos/seed/comment2/200",
        nickname = "midzy_01",
        content = "공지 기다리고 있었어요!",
        createdAt = 0L,
        likeCount = 124L,
        replyCount = 0L,
        isLiked = true,
        authorType = CommentAuthorType.USER,
    ),
)

private val previewOnstageUiState =
    OnstageUiState.Success(
        items = previewOnstageItems,
    )

private val previewFanRadarUiState =
    FanRadarUiState.Success(
        artists = previewFanRadarArtists,
    )

private val previewMyArtistFeedUiState =
    MyArtistFeedUiState.Success(
        items = previewMyArtistFeedItems,
        hasNext = true,
    )

private val previewCommentSheetUiState =
    CommentSheetUiState.Success(
        commentCount = previewComments.size.toLong(),
        comments = previewComments,
        repliesByCommentId = emptyMap(),
        expandedCommentIds = emptySet(),
    )