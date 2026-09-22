package com.ssafy.motif.feature.home.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.motif.core.designsystem.component.ClickableSurface
import com.ssafy.motif.core.designsystem.component.Text
import com.ssafy.motif.core.designsystem.theme.MotifTheme
import com.ssafy.motif.core.ui.comment.CommentUiState
import com.ssafy.motif.core.ui.comment.ModalCommentBottomSheet
import com.ssafy.motif.feature.home.impl.component.FanRadar
import com.ssafy.motif.feature.home.impl.component.OnstageCarousel
import com.ssafy.motif.feature.home.impl.component.myArtistFeed
import com.ssafy.motif.feature.home.impl.model.FanRadarUiState
import com.ssafy.motif.feature.home.impl.model.FeedAppendUiState
import com.ssafy.motif.feature.home.impl.model.MyArtistFeedItem
import com.ssafy.motif.feature.home.impl.model.MyArtistFeedUiState
import com.ssafy.motif.feature.home.impl.model.OnstageUiState

@Composable
internal fun HomeScreen(
    onOnstageClick: (Long) -> Unit,
    onFanRadarClick: () -> Unit,
    onArtistClick: (Long) -> Unit,
    onMotifClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val onstageUiState by
    viewModel.onstageUiState.collectAsStateWithLifecycle()

    val fanRadarUiState by
    viewModel.fanRadarUiState.collectAsStateWithLifecycle()

    val myArtistFeedUiState by
    viewModel.myArtistFeedUiState.collectAsStateWithLifecycle()

    val commentUiState by
    viewModel.commentUiState.collectAsStateWithLifecycle()

    HomeScreen(
        onstageUiState = onstageUiState,
        fanRadarUiState = fanRadarUiState,
        myArtistFeedUiState = myArtistFeedUiState,
        commentUiState = commentUiState,
        onOnstageClick = onOnstageClick,
        onFanRadarClick = onFanRadarClick,
        onArtistClick = onArtistClick,
        onMotifClick = onMotifClick,
        onNoticeLikeClick = viewModel::toggleNoticeLike,
        onNoticeCommentClick = viewModel::loadNoticeComments,
        onCommentLikeClick = viewModel::toggleCommentLike,
        onReplyClick = viewModel::loadReplies,
        onCommentBackClick = viewModel::backToComments,
        onCommentSendClick = viewModel::sendComment,
        onCommentDismiss = viewModel::dismissComments,
        onLoadMoreMyArtistFeed = viewModel::loadMoreMyArtistFeed,
        onOnstageRetryClick = viewModel::retryOnstage,
        onFanRadarRetryClick = viewModel::retryFanRadar,
        onMyArtistFeedRetryClick = viewModel::retryMyArtistFeed,
        onMyArtistFeedLoadMoreRetryClick =
            viewModel::retryLoadMoreMyArtistFeed,
        modifier = modifier,
    )
}

@Composable
private fun HomeScreen(
    onstageUiState: OnstageUiState,
    fanRadarUiState: FanRadarUiState,
    myArtistFeedUiState: MyArtistFeedUiState,
    commentUiState: CommentUiState,
    onOnstageClick: (Long) -> Unit,
    onFanRadarClick: () -> Unit,
    onArtistClick: (Long) -> Unit,
    onMotifClick: (Long) -> Unit,
    onNoticeLikeClick: (Long) -> Unit,
    onNoticeCommentClick: (Long) -> Unit,
    onCommentLikeClick: (Long) -> Unit,
    onReplyClick: (Long) -> Unit,
    onCommentBackClick: () -> Unit,
    onCommentSendClick: (String) -> Unit,
    onCommentDismiss: () -> Unit,
    onLoadMoreMyArtistFeed: () -> Unit,
    onOnstageRetryClick: () -> Unit,
    onFanRadarRetryClick: () -> Unit,
    onMyArtistFeedRetryClick: () -> Unit,
    onMyArtistFeedLoadMoreRetryClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedNoticeId by rememberSaveable {
        mutableStateOf<Long?>(null)
    }

    val selectedNotice =
        (myArtistFeedUiState as? MyArtistFeedUiState.Success)
            ?.items
            ?.filterIsInstance<MyArtistFeedItem.NoticeItem>()
            ?.firstOrNull { item ->
                item.notice.id == selectedNoticeId
            }
            ?.notice

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                MotifTheme.colors.background,
            )
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Top +
                            WindowInsetsSides.Horizontal,
                ),
            ),
    ) {
        HomeContent(
            onstageUiState = onstageUiState,
            fanRadarUiState = fanRadarUiState,
            myArtistFeedUiState = myArtistFeedUiState,
            onOnstageClick = onOnstageClick,
            onFanRadarClick = onFanRadarClick,
            onArtistClick = onArtistClick,
            onMotifClick = onMotifClick,
            onNoticeLikeClick = onNoticeLikeClick,
            onNoticeCommentClick = { noticeId ->
                selectedNoticeId = noticeId
                onNoticeCommentClick(noticeId)
            },
            onLoadMoreMyArtistFeed = onLoadMoreMyArtistFeed,
            onOnstageRetryClick = onOnstageRetryClick,
            onFanRadarRetryClick = onFanRadarRetryClick,
            onMyArtistFeedRetryClick = onMyArtistFeedRetryClick,
            onMyArtistFeedLoadMoreRetryClick =
                onMyArtistFeedLoadMoreRetryClick,
            modifier = Modifier.fillMaxSize(),
        )

        selectedNotice?.let { notice ->
            ModalCommentBottomSheet(
                commentCount = notice.commentCount,
                state = commentUiState,
                onDismissRequest = {
                    selectedNoticeId = null
                    onCommentDismiss()
                },
                onLikeClick = onCommentLikeClick,
                onReplyClick = onReplyClick,
                onBackClick = onCommentBackClick,
                onCommentSendClick = onCommentSendClick,
            )
        }
    }
}

@Composable
private fun HomeContent(
    onstageUiState: OnstageUiState,
    fanRadarUiState: FanRadarUiState,
    myArtistFeedUiState: MyArtistFeedUiState,
    onOnstageClick: (Long) -> Unit,
    onFanRadarClick: () -> Unit,
    onArtistClick: (Long) -> Unit,
    onMotifClick: (Long) -> Unit,
    onNoticeLikeClick: (Long) -> Unit,
    onNoticeCommentClick: (Long) -> Unit,
    onLoadMoreMyArtistFeed: () -> Unit,
    onOnstageRetryClick: () -> Unit,
    onFanRadarRetryClick: () -> Unit,
    onMyArtistFeedRetryClick: () -> Unit,
    onMyArtistFeedLoadMoreRetryClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState =
        rememberLazyListState()

    val feedSuccessState =
        myArtistFeedUiState as? MyArtistFeedUiState.Success

    val canLoadMore =
        feedSuccessState?.run {
            hasNext &&
                    appendState is FeedAppendUiState.Idle
        } ?: false

    val shouldLoadMore by remember(
        listState,
        canLoadMore,
    ) {
        derivedStateOf {
            if (!canLoadMore) {
                return@derivedStateOf false
            }

            val layoutInfo =
                listState.layoutInfo

            val lastVisibleItemIndex =
                layoutInfo.visibleItemsInfo
                    .lastOrNull()
                    ?.index
                    ?: return@derivedStateOf false

            layoutInfo.totalItemsCount > 0 &&
                    lastVisibleItemIndex >=
                    layoutInfo.totalItemsCount -
                    LOAD_MORE_THRESHOLD
        }
    }

    LaunchedEffect(
        shouldLoadMore,
    ) {
        if (shouldLoadMore) {
            onLoadMoreMyArtistFeed()
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(
            bottom = 32.dp,
        ),
        verticalArrangement =
            Arrangement.spacedBy(
                16.dp,
            ),
    ) {
        item(
            key = "hot-title",
            contentType = "hot-title",
        ) {
            Text(
                text = "HOT 🔥",
                color = MotifTheme.colors.typo,
                style = MotifTheme.typography.titleB22,
                modifier = Modifier.padding(
                    horizontal = 20.dp,
                ),
            )
        }

        item(
            key = "onstage",
            contentType = "onstage",
        ) {
            when (onstageUiState) {
                OnstageUiState.Loading -> {
                    OnstageLoadingContent()
                }

                is OnstageUiState.Success -> {
                    OnstageCarousel(
                        items = onstageUiState.items,
                        onVideoClick = onOnstageClick,
                    )
                }

                is OnstageUiState.Error -> {
                    SectionErrorContent(
                        onRetryClick = onOnstageRetryClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = 16.dp,
                            ),
                    )
                }
            }
        }

        item(
            key = "fan-radar",
            contentType = "fan-radar",
        ) {
            when (fanRadarUiState) {
                FanRadarUiState.Loading -> {
                    FanRadarLoadingContent(
                        modifier = Modifier.padding(
                            horizontal = 16.dp,
                        ),
                    )
                }

                is FanRadarUiState.Success -> {
                    FanRadar(
                        artists = fanRadarUiState.artists,
                        onClick = onFanRadarClick,
                        modifier = Modifier.padding(
                            horizontal = 16.dp,
                        ),
                    )
                }

                is FanRadarUiState.Error -> {
                    SectionErrorContent(
                        onRetryClick = onFanRadarRetryClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = 16.dp,
                            ),
                    )
                }
            }
        }

        when (myArtistFeedUiState) {
            MyArtistFeedUiState.Loading -> {
                item(
                    key = "my-artist-feed-loading",
                    contentType = "my-artist-feed-loading",
                ) {
                    FeedLoadingContent()
                }
            }

            is MyArtistFeedUiState.Error -> {
                item(
                    key = "my-artist-feed-error",
                    contentType = "my-artist-feed-error",
                ) {
                    SectionErrorContent(
                        onRetryClick = onMyArtistFeedRetryClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = 16.dp,
                            ),
                    )
                }
            }

            is MyArtistFeedUiState.Success -> {
                myArtistFeed(
                    items = myArtistFeedUiState.items,
                    onArtistClick = onArtistClick,
                    onMotifClick = onMotifClick,
                    onNoticeLikeClick = onNoticeLikeClick,
                    onNoticeCommentClick = onNoticeCommentClick,
                )

                when (myArtistFeedUiState.appendState) {
                    FeedAppendUiState.Idle -> Unit

                    FeedAppendUiState.Loading -> {
                        item(
                            key = "my-artist-feed-loading-more",
                            contentType = "my-artist-feed-loading-more",
                        ) {
                            FeedLoadingMoreContent()
                        }
                    }

                    is FeedAppendUiState.Error -> {
                        item(
                            key = "my-artist-feed-load-more-error",
                            contentType = "my-artist-feed-load-more-error",
                        ) {
                            FeedLoadMoreErrorContent(
                                onRetryClick =
                                    onMyArtistFeedLoadMoreRetryClick,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OnstageLoadingContent(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = 40.dp,
            )
            .aspectRatio(
                9f / 16f,
            ),
        contentAlignment =
            Alignment.Center,
    ) {
        LoadingText()
    }
}

@Composable
private fun FanRadarLoadingContent(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(
                112.dp,
            ),
        contentAlignment =
            Alignment.Center,
    ) {
        LoadingText()
    }
}

@Composable
private fun FeedLoadingContent(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(
                180.dp,
            ),
        contentAlignment =
            Alignment.Center,
    ) {
        LoadingText()
    }
}

@Composable
private fun FeedLoadingMoreContent(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                vertical = 24.dp,
            ),
        contentAlignment =
            Alignment.Center,
    ) {
        LoadingText()
    }
}

@Composable
private fun LoadingText(
    modifier: Modifier = Modifier,
) {
    Text(
        text = "불러오는 중...",
        color = MotifTheme.colors.typo,
        style = MotifTheme.typography.bodyR16,
        modifier = modifier,
    )
}

@Composable
private fun SectionErrorContent(
    onRetryClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(
            vertical = 24.dp,
        ),
        horizontalAlignment =
            Alignment.CenterHorizontally,
    ) {
        Text(
            text = "데이터를 불러오지 못했어요.",
            color = MotifTheme.colors.typo,
            style = MotifTheme.typography.bodyR16,
        )

        RetryButton(
            onClick = onRetryClick,
            modifier = Modifier.padding(
                top = 8.dp,
            ),
        )
    }
}

@Composable
private fun FeedLoadMoreErrorContent(
    onRetryClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                vertical = 20.dp,
            ),
        horizontalAlignment =
            Alignment.CenterHorizontally,
    ) {
        Text(
            text = "추가 콘텐츠를 불러오지 못했어요.",
            color = MotifTheme.colors.typo,
            style = MotifTheme.typography.bodyR16,
        )

        RetryButton(
            onClick = onRetryClick,
            modifier = Modifier.padding(
                top = 8.dp,
            ),
        )
    }
}

@Composable
private fun RetryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ClickableSurface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(
            8.dp,
        ),
        color = Color.Transparent,
    ) {
        Text(
            text = "다시 시도",
            color = MotifTheme.colors.white,
            style = MotifTheme.typography.bodySB16,
            modifier = Modifier.padding(
                horizontal = 16.dp,
                vertical = 8.dp,
            ),
        )
    }
}

@Preview(
    showBackground = true,
)
@Composable
private fun HomeScreenPreview(
    @PreviewParameter(
        HomeScreenPreviewParameterProvider::class,
    )
    previewData: HomeScreenPreviewData,
) {
    MotifTheme {
        HomeScreen(
            onstageUiState = previewData.onstageUiState,
            fanRadarUiState = previewData.fanRadarUiState,
            myArtistFeedUiState = previewData.myArtistFeedUiState,
            commentUiState = previewData.commentUiState,
            onOnstageClick = {},
            onFanRadarClick = {},
            onArtistClick = {},
            onMotifClick = {},
            onNoticeLikeClick = {},
            onNoticeCommentClick = {},
            onCommentLikeClick = {},
            onReplyClick = {},
            onCommentBackClick = {},
            onCommentSendClick = {},
            onCommentDismiss = {},
            onLoadMoreMyArtistFeed = {},
            onOnstageRetryClick = {},
            onFanRadarRetryClick = {},
            onMyArtistFeedRetryClick = {},
            onMyArtistFeedLoadMoreRetryClick = {},
        )
    }
}

private const val LOAD_MORE_THRESHOLD = 3