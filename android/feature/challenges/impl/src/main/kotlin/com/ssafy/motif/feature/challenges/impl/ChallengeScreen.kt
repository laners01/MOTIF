package com.ssafy.motif.feature.challenges.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import com.ssafy.motif.core.designsystem.component.bottomsheet.SheetState
import com.ssafy.motif.core.designsystem.component.bottomsheet.rememberModalBottomSheetState
import com.ssafy.motif.core.designsystem.theme.MotifTheme
import com.ssafy.motif.core.model.challenges.ChallengeShortForm
import com.ssafy.motif.core.model.comment.Comment
import com.ssafy.motif.core.model.comment.CommentAuthorType
import com.ssafy.motif.core.model.comment.Reply
import com.ssafy.motif.core.model.home.Artist
import com.ssafy.motif.core.ui.challenges.ChallengeDescriptionBottomSheet
import com.ssafy.motif.core.ui.comment.CommentBottomSheet
import com.ssafy.motif.core.ui.video.VideoShortsFeed
import com.ssafy.motif.core.ui.video.rememberVideoShortsState
import com.ssafy.motif.feature.challenges.impl.component.ChallengeBottomNavigation
import com.ssafy.motif.feature.challenges.impl.component.ChallengeShortFormOverlay
import com.ssafy.motif.core.designsystem.R as DesignSystemR

@Composable
internal fun ChallengeScreen(
    challenges: List<ChallengeShortForm>,
    myProfileImageUrl: String,
    comments: List<Comment>,
    repliesByCommentId: Map<Long, List<Reply>>,
    expandedCommentIds: Set<Long>,
    onArtistClick: (Long) -> Unit,
    onSubscribeClick: (Long) -> Unit,
    onLikeClick: (Long) -> Unit,
    onCommentClick: (Long) -> Unit,
    onCommentLikeClick: (Long) -> Unit,
    onReplyClick: (Long) -> Unit,
    onReplyLikeClick: (Long) -> Unit,
    onCaptureClick: (Long) -> Unit,
    onHomeClick: () -> Unit,
    onMyClick: () -> Unit,
    onMotifClick: () -> Unit,
    formatCommentCreatedAt: (Long) -> String,
    modifier: Modifier = Modifier,
) {
    val videoShortsState =
        rememberVideoShortsState()

    val descriptionSheetState =
        rememberModalBottomSheetState(
            skipPartiallyExpanded = false,
            partiallyExpandedFraction =
                DESCRIPTION_SHEET_FRACTION,
        )

    val commentSheetState =
        rememberModalBottomSheetState(
            skipPartiallyExpanded = false,
            partiallyExpandedFraction =
                COMMENT_SHEET_FRACTION,
        )

    var descriptionChallenge by remember {
        mutableStateOf<ChallengeShortForm?>(
            null,
        )
    }

    var commentChallenge by remember {
        mutableStateOf<ChallengeShortForm?>(
            null,
        )
    }

    val activeSheetState =
        when {
            descriptionChallenge != null ->
                descriptionSheetState

            commentChallenge != null ->
                commentSheetState

            else ->
                null
        }

    val isBottomSheetVisible =
        activeSheetState != null

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Color.Black,
            ),
    ) {
        Column(
            modifier =
                Modifier.fillMaxSize(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                VideoShortsFeed(
                    itemCount =
                        challenges.size,
                    videoId = { index ->
                        challenges[index]
                            .id
                            .toString()
                    },
                    videoUrl = { index ->
                        challenges[index]
                            .videoUrl
                    },
                    thumbnailUrl = { index ->
                        challenges[index]
                            .thumbnailUrl
                    },
                    state =
                        videoShortsState,
                    muted =
                        false,
                    repeat =
                        true,
                    captureBackdrop =
                        true,

                    /*
                     * BottomSheet가 떠 있는 동안
                     * VerticalPager 이동 금지.
                     *
                     * 영상 클릭에 의한 play/pause는
                     * 그대로 가능하다.
                     */
                    userScrollEnabled =
                        !isBottomSheetVisible,

                    modifier = Modifier
                        .fillMaxSize()
                        .scaleForBottomSheet(
                            sheetState =
                                activeSheetState,
                        ),
                ) {
                        index,
                        _,
                        backdropState,
                    ->

                    /*
                     * Sheet가 떠 있지 않을 때만
                     * 챌린지 Overlay를 그린다.
                     */
                    if (!isBottomSheetVisible) {
                        val challenge =
                            challenges[index]

                        ChallengeShortFormOverlay(
                            challenge =
                                challenge,
                            backdropState =
                                backdropState,
                            onArtistClick = {
                                onArtistClick(
                                    challenge
                                        .artist
                                        .id,
                                )
                            },
                            onSubscribeClick = {
                                onSubscribeClick(
                                    challenge
                                        .artist
                                        .id,
                                )
                            },
                            onDescriptionClick = {
                                descriptionChallenge =
                                    challenge
                            },
                            onLikeClick = {
                                onLikeClick(
                                    challenge.id,
                                )
                            },
                            onCommentClick = {
                                commentChallenge =
                                    challenge

                                onCommentClick(
                                    challenge.id,
                                )
                            },
                            onCaptureClick = {
                                onCaptureClick(
                                    challenge.id,
                                )
                            },
                        )
                    }
                }
            }

            ChallengeBottomNavigation(
                playbackProgress =
                    videoShortsState
                        .playbackProgress,
                myProfileImageUrl =
                    myProfileImageUrl,
                onSeek = { progress ->
                    videoShortsState.seekTo(
                        progress,
                    )
                },
                onHomeClick =
                    onHomeClick,
                onMyClick =
                    onMyClick,
                onMotifClick =
                    onMotifClick,
            )
        }

        descriptionChallenge
            ?.let { challenge ->
                ChallengeDescriptionBottomSheet(
                    challenge =
                        challenge,
                    sheetState =
                        descriptionSheetState,
                    onDismissRequest = {
                        descriptionChallenge =
                            null
                    },
                    onArtistClick =
                        onArtistClick,
                )
            }

        commentChallenge
            ?.let { challenge ->
                CommentBottomSheet(
                    commentCount =
                        challenge.commentCount,
                    comments =
                        comments,
                    repliesByCommentId =
                        repliesByCommentId,
                    expandedCommentIds =
                        expandedCommentIds,
                    sheetState =
                        commentSheetState,
                    onDismissRequest = {
                        commentChallenge =
                            null
                    },
                    onCommentLikeClick =
                        onCommentLikeClick,
                    onReplyClick =
                        onReplyClick,
                    onReplyLikeClick =
                        onReplyLikeClick,
                    formatCreatedAt =
                        formatCommentCreatedAt,
                )
            }
    }
}

/**
 * 기존 VideoShortsFeed의 레이아웃 크기는 유지하고,
 * BottomSheet의 top 위치에 맞춰 영상 전체를
 * 동일 비율로 축소한다.
 *
 * scaleX == scaleY 이므로 영상 비율은 유지된다.
 *
 * Hidden
 *   -> scale 1f
 *
 * PartiallyExpanded
 *   -> sheet top / video height
 *
 * Expanded
 *   -> scale 0f
 */
private fun Modifier.scaleForBottomSheet(
    sheetState: SheetState?,
): Modifier =
    graphicsLayer {
        val sheetTop =
            sheetState
                ?.offsetOrNull()

        val scale =
            if (
                sheetTop == null ||
                size.height <= 0f
            ) {
                1f
            } else {
                (
                        sheetTop /
                                size.height
                        ).coerceIn(
                        0f,
                        1f,
                    )
            }

        scaleX =
            scale

        scaleY =
            scale

        /*
         * 상단 중앙을 기준으로 축소.
         *
         * 세로 영상이 작아질수록 좌우에
         * 검정 여백이 생기며 비율은 유지된다.
         */
        transformOrigin =
            TransformOrigin(
                pivotFractionX = 0.5f,
                pivotFractionY = 0f,
            )

        alpha =
            if (scale == 0f) {
                0f
            } else {
                1f
            }
    }

private const val DESCRIPTION_SHEET_FRACTION =
    0.45f

private const val COMMENT_SHEET_FRACTION =
    0.6f

@Preview(
    showBackground = true,
)
@Composable
private fun ChallengeScreenPreview() {
    val context =
        LocalContext.current

    val profileImageUrl =
        "android.resource://${context.packageName}/${DesignSystemR.drawable.img_profile}"

    MotifTheme {
        ChallengeScreen(
            challenges =
                previewChallenges(
                    profileImageUrl =
                        profileImageUrl,
                ),
            myProfileImageUrl =
                profileImageUrl,
            comments =
                previewComments(
                    profileImageUrl =
                        profileImageUrl,
                ),
            repliesByCommentId =
                previewRepliesByCommentId(
                    profileImageUrl =
                        profileImageUrl,
                ),
            expandedCommentIds =
                setOf(30L),
            onArtistClick = {},
            onSubscribeClick = {},
            onLikeClick = {},
            onCommentClick = {},
            onCommentLikeClick = {},
            onReplyClick = {},
            onReplyLikeClick = {},
            onCaptureClick = {},
            onHomeClick = {},
            onMyClick = {},
            onMotifClick = {},
            formatCommentCreatedAt = {
                "1시간 전"
            },
        )
    }
}

private fun previewChallenges(
    profileImageUrl: String,
): List<ChallengeShortForm> {
    val yeji =
        Artist(
            id = 1L,
            name = "Yeji",
            profileImageUrl =
                profileImageUrl,
            groupName = "ITZY",
        )

    val chaeryeong =
        Artist(
            id = 2L,
            name = "Chaeryeong",
            profileImageUrl =
                profileImageUrl,
            groupName = "ITZY",
        )

    val haewon =
        Artist(
            id = 3L,
            name = "Haewon",
            profileImageUrl =
                profileImageUrl,
            groupName = "NMIXX",
        )

    val jennie =
        Artist(
            id = 4L,
            name = "Jennie",
            profileImageUrl =
                profileImageUrl,
            groupName = "BLACKPINK",
        )

    val challengeArtists =
        listOf(
            yeji,
            chaeryeong,
            haewon,
            jennie,
        )

    return listOf(
        ChallengeShortForm(
            id = 1L,
            videoUrl =
                "https://d21so2cpi8si9r.cloudfront.net/challenges/d7f49b37-54e7-4e2f-888e-3d24f6f453d4/master.m3u8",
            thumbnailUrl =
                "https://d21so2cpi8si9r.cloudfront.net/challenges/d7f49b37-54e7-4e2f-888e-3d24f6f453d4/thumb.jpg",
            artist = yeji,
            description =
                "생각보다 어렵지 않으니까 같이 도전해봐요 🖤",
            songArtistName =
                "JENNIE",
            songTitle =
                "like JENNY",
            likeCount =
                12_000L,
            commentCount =
                2_400L,
            participantCount =
                800L,
            viewCount =
                617_988L,
            createdAt =
                1_788_105_600_000L,
            isLiked =
                false,
            isSubscribed =
                false,
            challengeArtists =
                challengeArtists,
        ),
        ChallengeShortForm(
            id = 2L,
            videoUrl =
                "https://d21so2cpi8si9r.cloudfront.net/challenges/5ece3915-c32b-4836-8b55-868e49a8d8f0/master.m3u8",
            thumbnailUrl =
                "https://d21so2cpi8si9r.cloudfront.net/challenges/5ece3915-c32b-4836-8b55-868e49a8d8f0/thumb.jpg",
            artist = chaeryeong,
            description =
                "이번 챌린지는 마지막 포즈가 포인트예요 🔥",
            songArtistName =
                "뽀로로",
            songTitle =
                "바라밤",
            likeCount =
                8_421L,
            commentCount =
                1_203L,
            participantCount =
                534L,
            viewCount =
                324_521L,
            createdAt =
                1_788_019_200_000L,
            isLiked =
                true,
            isSubscribed =
                false,
            challengeArtists =
                challengeArtists.take(2),
        ),
        ChallengeShortForm(
            id = 3L,
            videoUrl =
                "https://d21so2cpi8si9r.cloudfront.net/challenges/a0006c4a-2746-4bf9-8bd1-bafa6e123b55/master.m3u8",
            thumbnailUrl =
                "https://d21so2cpi8si9r.cloudfront.net/challenges/a0006c4a-2746-4bf9-8bd1-bafa6e123b55/thumb.jpg",
            artist = haewon,
            description =
                "손동작만 잘 따라오면 금방 할 수 있어요 🙌",
            songArtistName =
                "See Tinh",
            songTitle =
                "Hoang Thuy Linh",
            likeCount =
                25_300L,
            commentCount =
                3_142L,
            participantCount =
                1_247L,
            viewCount =
                892_310L,
            createdAt =
                1_787_932_800_000L,
            isLiked =
                false,
            isSubscribed =
                true,
            challengeArtists =
                challengeArtists.take(3),
        ),
        ChallengeShortForm(
            id = 4L,
            videoUrl =
                "https://d21so2cpi8si9r.cloudfront.net/challenges/7dfa4de1-792a-41ec-a438-f51c48c15b1e/master.m3u8",
            thumbnailUrl =
                "https://d21so2cpi8si9r.cloudfront.net/challenges/7dfa4de1-792a-41ec-a438-f51c48c15b1e/thumb.jpg",
            artist = jennie,
            description =
                "같이 맞춰보면 더 재밌는 챌린지 ✨",
            songArtistName =
                "Shams",
            songTitle =
                "Break Your Legs",
            likeCount =
                5_692L,
            commentCount =
                821L,
            participantCount =
                327L,
            viewCount =
                201_746L,
            createdAt =
                1_787_846_400_000L,
            isLiked =
                true,
            isSubscribed =
                true,
            challengeArtists =
                challengeArtists,
        ),
        ChallengeShortForm(
            id = 5L,
            videoUrl =
                "https://d21so2cpi8si9r.cloudfront.net/challenges/d7f49b37-54e7-4e2f-888e-3d24f6f453d4/master.m3u8",
            thumbnailUrl =
                "https://d21so2cpi8si9r.cloudfront.net/challenges/d7f49b37-54e7-4e2f-888e-3d24f6f453d4/thumb.jpg",
            artist = yeji,
            description =
                "여러분 버전도 궁금해요! 같이 도전해봐요 😎",
            songArtistName =
                "JENNIE",
            songTitle =
                "like JENNY",
            likeCount =
                41_200L,
            commentCount =
                5_317L,
            participantCount =
                2_104L,
            viewCount =
                1_204_932L,
            createdAt =
                1_787_760_000_000L,
            isLiked =
                false,
            isSubscribed =
                false,
            challengeArtists =
                challengeArtists.takeLast(3),
        ),
    )
}

private fun previewComments(
    profileImageUrl: String,
): List<Comment> =
    listOf(
        Comment(
            id = 10L,
            profileImageUrl =
                profileImageUrl,
            nickname =
                "dance_with_me",
            content =
                "이 부분 안무 진짜 중독성 있다 ㅋㅋ",
            createdAt = 0L,
            likeCount = 84L,
            replyCount = 2L,
            isLiked = false,
            authorType =
                CommentAuthorType.USER,
        ),
        Comment(
            id = 20L,
            profileImageUrl =
                profileImageUrl,
            nickname =
                "motif_me",
            content =
                "나도 방금 찍어봤는데 생각보다 어렵네 😂",
            createdAt = 0L,
            likeCount = 16L,
            replyCount = 1L,
            isLiked = true,
            authorType =
                CommentAuthorType.CURRENT_USER,
        ),
        Comment(
            id = 30L,
            profileImageUrl =
                profileImageUrl,
            nickname =
                "YEJI",
            content =
                "다들 너무 잘 따라 해줘서 고마워요 🖤",
            createdAt = 0L,
            likeCount = 2_841L,
            replyCount = 32L,
            isLiked = false,
            authorType =
                CommentAuthorType.ARTIST,
        ),
        Comment(
            id = 40L,
            profileImageUrl =
                profileImageUrl,
            nickname =
                "winterluv",
            content =
                "영상 보자마자 바로 참여하러 갑니다",
            createdAt = 0L,
            likeCount = 53L,
            replyCount = 3L,
            isLiked = false,
            authorType =
                CommentAuthorType.USER,
        ),
        Comment(
            id = 50L,
            profileImageUrl =
                profileImageUrl,
            nickname =
                "chaechae",
            content =
                "마지막 동작이 제일 어려운 것 같아요 😭",
            createdAt = 0L,
            likeCount = 41L,
            replyCount = 0L,
            isLiked = false,
            authorType =
                CommentAuthorType.USER,
        ),
        Comment(
            id = 60L,
            profileImageUrl =
                profileImageUrl,
            nickname =
                "dance_daily",
            content =
                "오늘 연습해서 꼭 올려야겠다",
            createdAt = 0L,
            likeCount = 28L,
            replyCount = 1L,
            isLiked = true,
            authorType =
                CommentAuthorType.USER,
        ),
        Comment(
            id = 70L,
            profileImageUrl =
                profileImageUrl,
            nickname =
                "with_motif",
            content =
                "노래랑 안무 조합 너무 좋다",
            createdAt = 0L,
            likeCount = 103L,
            replyCount = 4L,
            isLiked = false,
            authorType =
                CommentAuthorType.USER,
        ),
        Comment(
            id = 80L,
            profileImageUrl =
                profileImageUrl,
            nickname =
                "jelly_dance",
            content =
                "친구랑 같이 찍으면 진짜 재밌을 듯 ㅋㅋ",
            createdAt = 0L,
            likeCount = 67L,
            replyCount = 2L,
            isLiked = false,
            authorType =
                CommentAuthorType.USER,
        ),
        Comment(
            id = 90L,
            profileImageUrl =
                profileImageUrl,
            nickname =
                "mymotif",
            content =
                "이거 계속 보게 되네",
            createdAt = 0L,
            likeCount = 152L,
            replyCount = 0L,
            isLiked = true,
            authorType =
                CommentAuthorType.USER,
        ),
        Comment(
            id = 100L,
            profileImageUrl =
                profileImageUrl,
            nickname =
                "challenge_go",
            content =
                "이번 주말에 바로 도전해봅니다 🔥",
            createdAt = 0L,
            likeCount = 35L,
            replyCount = 1L,
            isLiked = false,
            authorType =
                CommentAuthorType.USER,
        ),
    )

private fun previewRepliesByCommentId(
    profileImageUrl: String,
): Map<Long, List<Reply>> =
    mapOf(
        30L to listOf(
            Reply(
                id = 301L,
                commentId = 30L,
                profileImageUrl =
                    profileImageUrl,
                nickname =
                    "yeji_fan",
                content =
                    "직접 댓글 남겨준 거 실화인가요 😭",
                createdAt = 0L,
                likeCount = 127L,
                isLiked = true,
            ),
            Reply(
                id = 302L,
                commentId = 30L,
                profileImageUrl =
                    profileImageUrl,
                nickname =
                    "motif_daily",
                content =
                    "다음 챌린지도 기다릴게요!",
                createdAt = 0L,
                likeCount = 38L,
                isLiked = false,
            ),
        ),
        40L to listOf(
            Reply(
                id = 401L,
                commentId = 40L,
                profileImageUrl =
                    profileImageUrl,
                nickname =
                    "dance_go",
                content =
                    "저도 방금 찍고 왔어요 ㅋㅋ",
                createdAt = 0L,
                likeCount = 12L,
                isLiked = false,
            ),
        ),
    )