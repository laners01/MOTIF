package com.ssafy.motif.feature.challenges.impl.artist

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ssafy.motif.core.designsystem.component.bottomsheet.rememberBottomSheetState
import com.ssafy.motif.core.designsystem.theme.MotifTheme
import com.ssafy.motif.core.model.challenges.ArtistChallenge
import com.ssafy.motif.core.model.comment.Comment
import com.ssafy.motif.core.model.comment.CommentAuthorType
import com.ssafy.motif.core.model.home.Artist
import com.ssafy.motif.core.ui.comment.CommentBottomSheet
import com.ssafy.motif.core.ui.comment.CommentPage
import com.ssafy.motif.core.ui.comment.CommentUiState
import com.ssafy.motif.feature.challenges.impl.artist.component.ArtistChallengeOverlay
import com.ssafy.motif.feature.challenges.impl.artist.component.ChallengeCollaboratorList
import com.ssafy.motif.feature.challenges.impl.component.ChallengeDetailBottomSheet
import com.ssafy.motif.feature.challenges.impl.component.ChallengeShortFormLayout
import com.ssafy.motif.feature.challenges.impl.component.KeyboardBackgroundScrim
import com.ssafy.motif.core.designsystem.R as DesignSystemR

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ArtistChallengeScreen(
    challenges: List<ArtistChallenge>,
    myProfileImageUrl: String,
    commentUiState: CommentUiState,
    onArtistClick: (Long) -> Unit,
    onSubscribeClick: (Long) -> Unit,
    onLikeClick: (Long) -> Unit,
    onCommentClick: (Long) -> Unit,
    onCommentLikeClick: (Long) -> Unit,
    onReplyClick: (Long) -> Unit,
    onCommentBackClick: () -> Unit,
    onCommentSendClick: (String) -> Unit,
    onCaptureClick: (Long) -> Unit,
    onHomeClick: () -> Unit,
    onMyClick: () -> Unit,
    onMotifClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current

    val descriptionSheetState = rememberBottomSheetState(
        skipPartiallyExpanded = false,
        partiallyExpandedFraction = DESCRIPTION_SHEET_FRACTION,
    )

    val commentSheetState = rememberBottomSheetState(
        skipPartiallyExpanded = false,
        partiallyExpandedFraction = COMMENT_SHEET_FRACTION,
    )

    var descriptionChallenge by remember {
        mutableStateOf<ArtistChallenge?>(null)
    }

    var commentChallenge by remember {
        mutableStateOf<ArtistChallenge?>(null)
    }

    val activeSheetState = when {
        descriptionChallenge != null -> descriptionSheetState
        commentChallenge != null -> commentSheetState
        else -> null
    }

    val isCommentKeyboardVisible =
        commentChallenge != null && WindowInsets.isImeVisible

    Box(
        modifier = modifier.fillMaxSize(),
    ) {
        ChallengeShortFormLayout(
            itemCount = challenges.size,
            videoId = { index ->
                challenges[index].id.toString()
            },
            videoUrl = { index ->
                challenges[index].videoUrl
            },
            thumbnailUrl = { index ->
                challenges[index].thumbnailUrl
            },
            myProfileImageUrl = myProfileImageUrl,
            activeSheetState = activeSheetState,
            onHomeClick = onHomeClick,
            onMyClick = onMyClick,
            onMotifClick = onMotifClick,
            modifier = Modifier.fillMaxSize(),
        ) { index, backdropState ->
            val challenge = challenges[index]

            ArtistChallengeOverlay(
                challenge = challenge,
                backdropState = backdropState,
                onArtistClick = {
                    onArtistClick(challenge.artist.id)
                },
                onSubscribeClick = {
                    onSubscribeClick(challenge.artist.id)
                },
                onDescriptionClick = {
                    descriptionChallenge = challenge
                },
                onLikeClick = {
                    onLikeClick(challenge.id)
                },
                onCommentClick = {
                    commentChallenge = challenge
                    onCommentClick(challenge.id)
                },
                onCaptureClick = {
                    onCaptureClick(challenge.id)
                },
            )
        }

        if (isCommentKeyboardVisible) {
            KeyboardBackgroundScrim(
                onClick = focusManager::clearFocus,
            )
        }

        descriptionChallenge?.let { challenge ->
            ChallengeDetailBottomSheet(
                description = challenge.description,
                viewCount = challenge.viewCount,
                likeCount = challenge.likeCount,
                createdAt = challenge.createdAt,
                sheetState = descriptionSheetState,
                onDismissRequest = {
                    descriptionChallenge = null
                },
                additionalContent = {
                    if (challenge.challengeArtists.isNotEmpty()) {
                        Spacer(
                            modifier = Modifier.height(20.dp),
                        )

                        ChallengeCollaboratorList(
                            artists = challenge.challengeArtists,
                            onArtistClick = onArtistClick,
                        )
                    }
                },
            )
        }

        commentChallenge?.let { challenge ->
            CommentBottomSheet(
                commentCount = challenge.commentCount,
                state = commentUiState,
                sheetState = commentSheetState,
                onDismissRequest = {
                    commentChallenge = null

                    if (commentUiState.page is CommentPage.Replies) {
                        onCommentBackClick()
                    }
                },
                onLikeClick = onCommentLikeClick,
                onReplyClick = onReplyClick,
                onBackClick = onCommentBackClick,
                onCommentSendClick = onCommentSendClick,
            )
        }
    }
}

@Preview(
    showBackground = true,
)
@Composable
private fun ArtistChallengeScreenPreview() {
    val context = LocalContext.current

    val profileImageUrl =
        "android.resource://${context.packageName}/${DesignSystemR.drawable.img_profile}"

    val comments = remember(profileImageUrl) {
        previewComments(
            profileImageUrl = profileImageUrl,
        )
    }

    var commentUiState by remember(comments) {
        mutableStateOf(
            CommentUiState(
                comments = comments,
            ),
        )
    }

    MotifTheme {
        ArtistChallengeScreen(
            challenges = previewChallenges(
                profileImageUrl = profileImageUrl,
            ),
            myProfileImageUrl = profileImageUrl,
            commentUiState = commentUiState,
            onArtistClick = {},
            onSubscribeClick = {},
            onLikeClick = {},
            onCommentClick = {
                commentUiState = CommentUiState(
                    comments = comments,
                )
            },
            onCommentLikeClick = {},
            onReplyClick = { commentId ->
                val parentComment = comments.firstOrNull { comment ->
                    comment.id == commentId
                } ?: return@ArtistChallengeScreen

                commentUiState = commentUiState.copy(
                    page = CommentPage.Replies(
                        parentComment = parentComment,
                    ),
                    replies = previewReplies(
                        profileImageUrl = profileImageUrl,
                    ),
                    isLoading = false,
                )
            },
            onCommentBackClick = {
                commentUiState = commentUiState.copy(
                    page = CommentPage.Comments,
                    replies = emptyList(),
                    isLoading = false,
                )
            },
            onCommentSendClick = {},
            onCaptureClick = {},
            onHomeClick = {},
            onMyClick = {},
            onMotifClick = {},
        )
    }
}

private fun previewChallenges(
    profileImageUrl: String,
): List<ArtistChallenge> {
    val yeji = Artist(
        id = 1L,
        name = "Yeji",
        profileImageUrl = profileImageUrl,
        groupName = "ITZY",
    )

    val chaeryeong = Artist(
        id = 2L,
        name = "Chaeryeong",
        profileImageUrl = profileImageUrl,
        groupName = "ITZY",
    )

    val haewon = Artist(
        id = 3L,
        name = "Haewon",
        profileImageUrl = profileImageUrl,
        groupName = "NMIXX",
    )

    val jennie = Artist(
        id = 4L,
        name = "Jennie",
        profileImageUrl = profileImageUrl,
        groupName = "BLACKPINK",
    )

    val challengeArtists = listOf(
        yeji,
        chaeryeong,
        haewon,
        jennie,
    )

    return listOf(
        ArtistChallenge(
            id = 1L,
            videoUrl = "https://d21so2cpi8si9r.cloudfront.net/challenges/d7f49b37-54e7-4e2f-888e-3d24f6f453d4/master.m3u8",
            thumbnailUrl = "https://d21so2cpi8si9r.cloudfront.net/challenges/d7f49b37-54e7-4e2f-888e-3d24f6f453d4/thumb.jpg",
            artist = yeji,
            description = "생각보다 어렵지 않으니까 같이 도전해봐요 🖤",
            songArtistName = "JENNIE",
            songTitle = "like JENNY",
            likeCount = 12_000L,
            commentCount = 2_400L,
            participantCount = 800L,
            viewCount = 617_988L,
            createdAt = 1_788_105_600_000L,
            isLiked = false,
            isSubscribed = false,
            challengeArtists = challengeArtists,
        ),
        ArtistChallenge(
            id = 2L,
            videoUrl = "https://d21so2cpi8si9r.cloudfront.net/challenges/5ece3915-c32b-4836-8b55-868e49a8d8f0/master.m3u8",
            thumbnailUrl = "https://d21so2cpi8si9r.cloudfront.net/challenges/5ece3915-c32b-4836-8b55-868e49a8d8f0/thumb.jpg",
            artist = chaeryeong,
            description = "이번 챌린지는 마지막 포즈가 포인트예요 🔥",
            songArtistName = "뽀로로",
            songTitle = "바라밤",
            likeCount = 8_421L,
            commentCount = 1_203L,
            participantCount = 534L,
            viewCount = 324_521L,
            createdAt = 1_788_019_200_000L,
            isLiked = true,
            isSubscribed = false,
            challengeArtists = challengeArtists.take(2),
        ),
    )
}

private fun previewComments(
    profileImageUrl: String,
): List<Comment> {
    val now = System.currentTimeMillis()

    return listOf(
        Comment(
            id = 10L,
            profileImageUrl = profileImageUrl,
            nickname = "dance_with_me",
            content = "이 부분 안무 진짜 중독성 있다 ㅋㅋ",
            createdAt = now - 30_000L,
            likeCount = 84L,
            replyCount = 2L,
            isLiked = false,
            authorType = CommentAuthorType.USER,
        ),
        Comment(
            id = 20L,
            profileImageUrl = profileImageUrl,
            nickname = "motif_me",
            content = "나도 방금 찍어봤는데 생각보다 어렵네 😂",
            createdAt = now - 5 * 60_000L,
            likeCount = 16L,
            replyCount = 1L,
            isLiked = true,
            authorType = CommentAuthorType.CURRENT_USER,
        ),
        Comment(
            id = 30L,
            profileImageUrl = profileImageUrl,
            nickname = "YEJI",
            content = "다들 너무 잘 따라 해줘서 고마워요 🖤",
            createdAt = now - 30 * 60_000L,
            likeCount = 2_841L,
            replyCount = 32L,
            isLiked = false,
            authorType = CommentAuthorType.ARTIST,
        ),
        Comment(
            id = 40L,
            profileImageUrl = profileImageUrl,
            nickname = "winterluv",
            content = "영상 보자마자 바로 참여하러 갑니다",
            createdAt = now - 2 * 60 * 60_000L,
            likeCount = 53L,
            replyCount = 3L,
            isLiked = false,
            authorType = CommentAuthorType.USER,
        ),
        Comment(
            id = 50L,
            profileImageUrl = profileImageUrl,
            nickname = "chaechae",
            content = "마지막 동작이 제일 어려운 것 같아요 😭",
            createdAt = now - 8 * 60 * 60_000L,
            likeCount = 41L,
            replyCount = 0L,
            isLiked = false,
            authorType = CommentAuthorType.USER,
        ),
        Comment(
            id = 60L,
            profileImageUrl = profileImageUrl,
            nickname = "dance_daily",
            content = "오늘 연습해서 꼭 올려야겠다",
            createdAt = now - 24 * 60 * 60_000L,
            likeCount = 28L,
            replyCount = 1L,
            isLiked = true,
            authorType = CommentAuthorType.USER,
        ),
    )
}

private fun previewReplies(
    profileImageUrl: String,
): List<Comment> {
    val now = System.currentTimeMillis()

    return listOf(
        Comment(
            id = 301L,
            profileImageUrl = profileImageUrl,
            nickname = "yeji_fan",
            content = "직접 댓글 남겨준 거 실화인가요 😭",
            createdAt = now - 15 * 60_000L,
            likeCount = 127L,
            replyCount = 0L,
            isLiked = true,
            authorType = CommentAuthorType.USER,
        ),
        Comment(
            id = 302L,
            profileImageUrl = profileImageUrl,
            nickname = "motif_daily",
            content = "다음 챌린지도 기다릴게요!",
            createdAt = now - 3 * 60 * 60_000L,
            likeCount = 38L,
            replyCount = 0L,
            isLiked = false,
            authorType = CommentAuthorType.USER,
        ),
    )
}

private const val DESCRIPTION_SHEET_FRACTION = 0.4f
private const val COMMENT_SHEET_FRACTION = 0.65f