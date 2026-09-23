package com.ssafy.motif.feature.mypage.impl.mypage

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.motif.core.designsystem.component.Button
import com.ssafy.motif.core.designsystem.component.LoadingWheel
import com.ssafy.motif.core.designsystem.component.MotifButtonDefaults
import com.ssafy.motif.core.designsystem.component.Surface
import com.ssafy.motif.core.designsystem.component.Tab
import com.ssafy.motif.core.designsystem.component.TabRow
import com.ssafy.motif.core.designsystem.component.TabRowDefaults
import com.ssafy.motif.core.designsystem.component.TabRowDefaults.tabIndicatorOffset
import com.ssafy.motif.core.designsystem.component.Text
import com.ssafy.motif.core.designsystem.theme.MotifTheme
import com.ssafy.motif.core.model.user.UserActivityTargetType
import com.ssafy.motif.core.ui.challenges.ChallengeThumbnail
import com.ssafy.motif.core.ui.motif.MotifVideoSection
import com.ssafy.motif.feature.mypage.impl.mypage.component.LikedChallengeCarousel
import com.ssafy.motif.feature.mypage.impl.mypage.component.LikedChallengeInfo
import com.ssafy.motif.feature.mypage.impl.mypage.component.MyCommentCard
import com.ssafy.motif.feature.mypage.impl.mypage.component.MyCommentEmptyContent
import com.ssafy.motif.feature.mypage.impl.mypage.component.MyProfileSection
import com.ssafy.motif.feature.mypage.impl.mypage.component.SubscribedArtistCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun MyPageScreen(
    onNotificationClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onCreateClick: () -> Unit = {},
    onChallengeClick: (Long) -> Unit = {},
    onLikedChallengeClick: (UserActivityTargetType, Long) -> Unit = { _, _ -> },
    onArtistSeeMoreClick: (Long) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: MyPageViewModel = hiltViewModel(),
) {
    val uiState by
    viewModel.uiState.collectAsStateWithLifecycle()

    val context =
        LocalContext.current

    val coroutineScope =
        rememberCoroutineScope()

    val launcher =
        rememberLauncherForActivityResult(
            contract =
                ActivityResultContracts.PickVisualMedia(),
        ) { uri ->
            if (uri == null) {
                return@rememberLauncherForActivityResult
            }

            coroutineScope.launch {
                runCatching {
                    readProfileImage(
                        context = context,
                        uri = uri,
                    )
                }.onSuccess { image ->
                    viewModel.updateProfileImage(
                        contentType =
                            image.contentType,
                        imageBytes =
                            image.bytes,
                    )
                }.onFailure(
                    viewModel::onProfileImageSelectionError,
                )
            }
        }

    MyPageScreen(
        uiState = uiState,
        onNotificationClick =
            onNotificationClick,
        onSettingsClick =
            onSettingsClick,
        onTabClick =
            viewModel::selectTab,
        onLoadMoreLiked =
            viewModel::onLoadMoreLiked,
        onCreateClick =
            onCreateClick,
        onChallengeClick =
            onChallengeClick,
        onLikedChallengeClick =
            onLikedChallengeClick,
        onArtistCardClick =
            viewModel::onArtistCardClick,
        onArtistSeeMoreClick =
            onArtistSeeMoreClick,
        onLikedFocusChange =
            viewModel::onLikedFocusChange,
        onNicknameEditStart =
            viewModel::onNicknameEditStart,
        onNicknameChange =
            viewModel::onNicknameChange,
        onNicknameEditDone =
            viewModel::onNicknameEditDone,
        onNicknameEditCancel =
            viewModel::onNicknameEditCancel,
        onProfileImageClick = {
            launcher.launch(
                PickVisualMediaRequest(
                    ActivityResultContracts
                        .PickVisualMedia
                        .ImageOnly,
                ),
            )
        },
        modifier = modifier.windowInsetsPadding(
            WindowInsets.safeDrawing.only(
                WindowInsetsSides.Top,
            ),
        ),
    )
}

@Composable
private fun MyPageScreen(
    uiState: MyPageUiState,
    onNotificationClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onTabClick: (MyPageTab) -> Unit,
    onLoadMoreLiked: () -> Unit,
    onCreateClick: () -> Unit,
    onChallengeClick: (Long) -> Unit,
    onLikedChallengeClick: (UserActivityTargetType, Long) -> Unit,
    onArtistCardClick: (Long) -> Unit,
    onArtistSeeMoreClick: (Long) -> Unit,
    onLikedFocusChange: (Int) -> Unit,
    onNicknameEditStart: () -> Unit,
    onNicknameChange: (String) -> Unit,
    onNicknameEditDone: () -> Unit,
    onNicknameEditCancel: () -> Unit,
    onProfileImageClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState =
        rememberLazyListState()

    LaunchedEffect(
        uiState.selectedTab,
    ) {
        listState.scrollToItem(0)
    }

    val likedSuccess =
        uiState.likedState
                as? MyLikedUiState.Success

    val canLoadMore =
        uiState.selectedTab ==
                MyPageTab.Liked &&
                likedSuccess != null &&
                likedSuccess.hasNext &&
                likedSuccess.appendState is MyLikedAppendUiState.Idle

    val shouldLoadMore by
    remember(
        listState,
        canLoadMore,
    ) {
        derivedStateOf {
            if (!canLoadMore) {
                return@derivedStateOf false
            }

            val layoutInfo =
                listState.layoutInfo

            val lastVisible =
                layoutInfo
                    .visibleItemsInfo
                    .lastOrNull()
                    ?.index
                    ?: return@derivedStateOf false

            layoutInfo.totalItemsCount > 0 &&
                    lastVisible >=
                    layoutInfo.totalItemsCount -
                    LOAD_MORE_THRESHOLD
        }
    }

    LaunchedEffect(
        shouldLoadMore,
    ) {
        if (shouldLoadMore) {
            onLoadMoreLiked()
        }
    }

    var listBottom by
    remember {
        mutableFloatStateOf(0f)
    }

    var headerBottom by
    remember {
        mutableFloatStateOf(0f)
    }

    val contentAreaHeight =
        with(LocalDensity.current) {
            (
                    listBottom -
                            headerBottom
                    )
                .coerceAtLeast(0f)
                .toDp()
        }

    Surface(
        modifier =
            modifier.fillMaxSize(),
    ) {
        LazyColumn(
            state = listState,
            modifier =
                Modifier
                    .fillMaxSize()
                    .onGloballyPositioned {
                        listBottom =
                            it.positionInRoot().y +
                                    it.size.height
                    },
        ) {
            when (
                val profileState =
                    uiState.profileState
            ) {
                ProfileUiState.Loading -> {
                    item {
                        ProfileLoading()
                    }
                }

                is ProfileUiState.Success -> {
                    item {
                        MyProfileSection(
                            profile =
                                profileState.profile,
                            nicknameEdit =
                                uiState.nicknameEdit,
                            onNicknameEditStart =
                                onNicknameEditStart,
                            onNicknameChange =
                                onNicknameChange,
                            onNicknameEditDone =
                                onNicknameEditDone,
                            onNicknameEditCancel =
                                onNicknameEditCancel,
                            onNotificationClick =
                                onNotificationClick,
                            onProfileImageClick =
                                onProfileImageClick,
                            onSettingsClick =
                                onSettingsClick,
                        )
                    }
                }

                is ProfileUiState.Error -> {
                    item { }
                }
            }

            uiState
                .representativeMotif
                ?.let { motif ->
                    item {
                        MotifVideoSection(
                            videoId =
                                motif.id.toString(),
                            videoUrl =
                                motif.videoUrl,
                            thumbnailUrl =
                                motif.thumbnailUrl,
                            modifier =
                                Modifier.padding(
                                    top = 4.dp,
                                    bottom = 12.dp,
                                ),
                        )
                    }
                }

            stickyHeader {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .background(
                                MotifTheme
                                    .colors
                                    .background,
                            )
                            .padding(
                                bottom = 24.dp,
                            )
                            .onGloballyPositioned {
                                headerBottom =
                                    it
                                        .positionInRoot()
                                        .y +
                                            it.size.height
                            },
                ) {
                    TabRow(
                        selectedTabIndex =
                            uiState
                                .selectedTab
                                .ordinal,
                        containerColor =
                            MotifTheme
                                .colors
                                .background,
                        divider = {},
                        indicator = {
                                tabPositions,
                            ->
                            if (
                                uiState
                                    .selectedTab
                                    .ordinal <
                                tabPositions.size
                            ) {
                                TabRowDefaults.Indicator(
                                    modifier =
                                        Modifier
                                            .tabIndicatorOffset(
                                                tabPositions[
                                                    uiState
                                                        .selectedTab
                                                        .ordinal
                                                ],
                                            )
                                            .height(
                                                2.dp,
                                            )
                                            .width(
                                                50.dp,
                                            ),
                                    color =
                                        MotifTheme
                                            .colors
                                            .pink,
                                )
                            }
                        },
                    ) {
                        MyPageTab.entries
                            .forEach { tab ->
                                Tab(
                                    selected =
                                        uiState
                                            .selectedTab ==
                                                tab,
                                    onClick = {
                                        onTabClick(
                                            tab,
                                        )
                                    },
                                    selectedContentColor =
                                        MotifTheme
                                            .colors
                                            .typo,
                                    unselectedContentColor =
                                        MotifTheme
                                            .colors
                                            .gray400,
                                ) {
                                    Text(
                                        text =
                                            tab.label,
                                        style =
                                            MotifTheme
                                                .typography
                                                .bodyR16,
                                        modifier =
                                            Modifier.padding(
                                                top =
                                                    16.dp,
                                                bottom =
                                                    8.dp,
                                            ),
                                    )
                                }
                            }
                    }
                }
            }

            when (
                uiState.selectedTab
            ) {
                MyPageTab.Challenge -> {
                    when (
                        val state =
                            uiState.challengeState
                    ) {
                        MyChallengeUiState.Loading -> {
                            item {
                                ChallengeLoadingContent()
                            }
                        }

                        is MyChallengeUiState.Success -> {
                            if (
                                state.challenges
                                    .isEmpty()
                            ) {
                                item {
                                    ChallengeEmptyContent(
                                        onCreateClick =
                                            onCreateClick,
                                        modifier =
                                            Modifier.height(
                                                contentAreaHeight,
                                            ),
                                    )
                                }
                            } else {
                                items(
                                    items =
                                        state
                                            .challenges
                                            .chunked(3),
                                    key = { row ->
                                        row.first().id
                                    },
                                ) { row ->
                                    Row(
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(
                                                    bottom =
                                                        2.dp,
                                                ),
                                        horizontalArrangement =
                                            Arrangement
                                                .spacedBy(
                                                    2.dp,
                                                ),
                                    ) {
                                        row.forEach {
                                                challenge,
                                            ->
                                            ChallengeThumbnail(
                                                thumbnailUrl =
                                                    challenge
                                                        .thumbnailUrl,
                                                viewCount =
                                                    challenge
                                                        .viewCount,
                                                onClick = {
                                                    onChallengeClick(
                                                        challenge.id,
                                                    )
                                                },
                                                modifier =
                                                    Modifier
                                                        .weight(
                                                            1f,
                                                        ),
                                            )
                                        }

                                        repeat(
                                            3 -
                                                    row.size,
                                        ) {
                                            Spacer(
                                                modifier =
                                                    Modifier
                                                        .weight(
                                                            1f,
                                                        ),
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        is MyChallengeUiState.Error -> {
                            item { }
                        }
                    }
                }

                MyPageTab.Artist -> {
                    when (
                        val state =
                            uiState.artistState
                    ) {
                        MyArtistUiState.Idle,
                        MyArtistUiState.Loading,
                            -> {
                            item {
                                ChallengeLoadingContent()
                            }
                        }

                        is MyArtistUiState.Success -> {
                            items(
                                items =
                                    state
                                        .artists
                                        .chunked(2),
                                key = { row ->
                                    row
                                        .first()
                                        .artist
                                        .id
                                },
                            ) { row ->
                                Row(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(
                                                horizontal =
                                                    24.dp,
                                            )
                                            .padding(
                                                bottom =
                                                    18.dp,
                                            ),
                                    horizontalArrangement =
                                        Arrangement
                                            .spacedBy(
                                                18.dp,
                                            ),
                                ) {
                                    row.forEach {
                                            artistSubscription,
                                        ->
                                        SubscribedArtistCard(
                                            artist =
                                                artistSubscription,
                                            isFlipped =
                                                uiState
                                                    .flippedArtistId ==
                                                        artistSubscription
                                                            .artist
                                                            .id,
                                            onClick = {
                                                onArtistCardClick(
                                                    artistSubscription
                                                        .artist
                                                        .id,
                                                )
                                            },
                                            onSeeMoreClick = {
                                                onArtistSeeMoreClick(
                                                    artistSubscription
                                                        .artist
                                                        .id,
                                                )
                                            },
                                            modifier =
                                                Modifier
                                                    .weight(
                                                        1f,
                                                    ),
                                        )
                                    }

                                    repeat(
                                        2 -
                                                row.size,
                                    ) {
                                        Spacer(
                                            modifier =
                                                Modifier
                                                    .weight(
                                                        1f,
                                                    ),
                                        )
                                    }
                                }
                            }
                        }

                        is MyArtistUiState.Error -> {
                            item { }
                        }
                    }
                }

                MyPageTab.Liked -> {
                    when (
                        val state =
                            uiState.likedState
                    ) {
                        MyLikedUiState.Idle,
                        MyLikedUiState.Loading,
                            -> {
                            item {
                                ChallengeLoadingContent()
                            }
                        }

                        is MyLikedUiState.Success -> {
                            if (
                                state.challenges
                                    .isNotEmpty()
                            ) {
                                item {
                                    LikedChallengeContent(
                                        challenges =
                                            state.challenges,
                                        focusedIndex =
                                            uiState.likedFocusedIndex,
                                        onFocusChange =
                                            onLikedFocusChange,
                                        onLikedChallengeClick =
                                            onLikedChallengeClick,
                                    )
                                }

                                if (
                                    state.appendState
                                            is MyLikedAppendUiState.Loading
                                ) {
                                    item {
                                        ChallengeLoadingMore()
                                    }
                                }
                            } else {
                                item {
                                    LikedChallengeEmptyContent(
                                        modifier =
                                            Modifier.height(
                                                contentAreaHeight,
                                            ),
                                    )
                                }
                            }
                        }

                        is MyLikedUiState.Error -> {
                            item {
                                Text(
                                    text = state.exception.stackTraceToString(),
                                    color = MotifTheme.colors.typo,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LikedChallengeContent(
    challenges: List<LikedChallengeUiModel>,
    focusedIndex: Int,
    onFocusChange: (Int) -> Unit,
    onLikedChallengeClick: (UserActivityTargetType, Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(
                    bottom = 126.dp,
                ),
        horizontalAlignment =
            Alignment.CenterHorizontally,
    ) {
        LikedChallengeCarousel(
            itemCount =
                challenges.size,
            thumbnailUrl = { index ->
                challenges[index]
                    .thumbnailUrl
            },
            onSettledPageChange =
                onFocusChange,
            onItemClick = { index ->
                val challenge =
                    challenges.getOrNull(
                        index,
                    ) ?: return@LikedChallengeCarousel

                onLikedChallengeClick(
                    challenge.targetType,
                    challenge.id,
                )
            },
        )

        challenges
            .getOrNull(
                focusedIndex,
            )
            ?.let { challenge ->
                LikedChallengeInfo(
                    likeCount =
                        challenge.likeCount,
                    modifier =
                        Modifier.padding(
                            top = 16.dp,
                        ),
                )

                when (
                    val commentState =
                        challenge.commentState
                ) {
                    LikedChallengeCommentUiState.Empty -> {
                        MyCommentEmptyContent(
                            modifier =
                                Modifier.padding(
                                    start = 16.dp,
                                    end = 16.dp,
                                    top = 28.dp,
                                ),
                        )
                    }

                    is LikedChallengeCommentUiState.Written -> {
                        MyCommentCard(
                            myComment =
                                commentState.myComment,
                            onClick = {},
                            modifier =
                                Modifier.padding(
                                    start = 16.dp,
                                    end = 16.dp,
                                    top = 28.dp,
                                ),
                        )
                    }
                }
            }
    }
}
@Composable
private fun ProfileLoading(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(
                    vertical = 40.dp,
                ),
        contentAlignment =
            Alignment.Center,
    ) {
        LoadingWheel()
    }
}

@Composable
private fun ChallengeLoadingContent(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(
                    vertical = 80.dp,
                ),
        contentAlignment =
            Alignment.Center,
    ) {
        LoadingWheel()
    }
}

@Composable
private fun ChallengeLoadingMore(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(
                    vertical = 24.dp,
                ),
        contentAlignment =
            Alignment.Center,
    ) {
        LoadingWheel()
    }
}

@Composable
private fun ChallengeEmptyContent(
    onCreateClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier.fillMaxWidth(),
        horizontalAlignment =
            Alignment.CenterHorizontally,
        verticalArrangement =
            Arrangement.spacedBy(
                32.dp,
                Alignment.CenterVertically,
            ),
    ) {
        Text(
            text =
                "아직 참여한 챌린지가 없어요. \n " +
                        "마음에 드는 챌린지를 찾아 참여해보세요!",
            style =
                MotifTheme
                    .typography
                    .titleR20,
            color =
                MotifTheme
                    .colors
                    .gray400,
            textAlign =
                TextAlign.Center,
        )

        Button(
            onClick =
                onCreateClick,
            shape =
                RoundedCornerShape(
                    20.dp,
                ),
            colors =
                MotifButtonDefaults
                    .buttonColors(
                        containerColor =
                            MotifTheme
                                .colors
                                .pink,
                        contentColor =
                            MotifTheme
                                .colors
                                .typo,
                    ),
        ) {
            Text(
                text =
                    "챌린지 둘러보기",
                style =
                    MotifTheme
                        .typography
                        .bodySB16,
            )
        }
    }
}

@Composable
private fun LikedChallengeEmptyContent(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier.fillMaxWidth(),
        contentAlignment =
            Alignment.Center,
    ) {
        Text(
            text =
                "아직 좋아요한 영상이 없어요",
            style =
                MotifTheme
                    .typography
                    .titleR20,
            color =
                MotifTheme
                    .colors
                    .gray400,
            textAlign =
                TextAlign.Center,
        )
    }
}

private suspend fun readProfileImage(
    context: Context,
    uri: Uri,
): SelectedProfileImage =
    withContext(
        Dispatchers.IO,
    ) {
        val resolver =
            context.contentResolver

        val contentType =
            resolver.getType(uri)
                ?: throw IllegalArgumentException(
                    "이미지 형식을 확인할 수 없습니다.",
                )

        require(
            contentType in
                    ALLOWED_PROFILE_IMAGE_TYPES,
        ) {
            "JPEG, PNG, WEBP 이미지만 사용할 수 있습니다."
        }

        val declaredSize =
            resolver
                .query(
                    uri,
                    arrayOf(
                        OpenableColumns.SIZE,
                    ),
                    null,
                    null,
                    null,
                )
                ?.use { cursor ->
                    val index =
                        cursor.getColumnIndex(
                            OpenableColumns.SIZE,
                        )

                    if (
                        index >= 0 &&
                        cursor.moveToFirst()
                    ) {
                        cursor
                            .getLong(index)
                            .takeIf {
                                it >= 0L
                            }
                    } else {
                        null
                    }
                }

        if (
            declaredSize != null &&
            declaredSize >
            MAX_PROFILE_IMAGE_BYTES
        ) {
            throw IllegalArgumentException(
                "프로필 이미지는 10MB 이하만 사용할 수 있습니다.",
            )
        }

        val bytes =
            resolver
                .openInputStream(uri)
                ?.use {
                    it.readBytes()
                }
                ?: throw IllegalStateException(
                    "이미지를 읽을 수 없습니다.",
                )

        require(
            bytes.isNotEmpty(),
        ) {
            "비어 있는 이미지입니다."
        }

        require(
            bytes.size.toLong() <=
                    MAX_PROFILE_IMAGE_BYTES,
        ) {
            "프로필 이미지는 10MB 이하만 사용할 수 있습니다."
        }

        SelectedProfileImage(
            contentType =
                contentType,
            bytes =
                bytes,
        )
    }

private data class SelectedProfileImage(
    val contentType: String,
    val bytes: ByteArray,
)

private const val LOAD_MORE_THRESHOLD =
    3

private const val MAX_PROFILE_IMAGE_BYTES =
    10L * 1024L * 1024L

private val ALLOWED_PROFILE_IMAGE_TYPES =
    setOf(
        "image/jpeg",
        "image/png",
        "image/webp",
    )