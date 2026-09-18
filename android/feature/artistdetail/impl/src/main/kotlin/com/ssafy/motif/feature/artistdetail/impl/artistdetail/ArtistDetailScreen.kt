package com.ssafy.motif.feature.artistdetail.impl.artistdetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.motif.core.designsystem.component.IconButton
import com.ssafy.motif.core.designsystem.component.LoadingWheel
import com.ssafy.motif.core.designsystem.component.ModifIconButtonDefaults
import com.ssafy.motif.core.designsystem.component.Surface
import com.ssafy.motif.core.designsystem.component.Tab
import com.ssafy.motif.core.designsystem.component.TabRow
import com.ssafy.motif.core.designsystem.component.TabRowDefaults
import com.ssafy.motif.core.designsystem.component.TabRowDefaults.tabIndicatorOffset
import com.ssafy.motif.core.designsystem.component.Text
import com.ssafy.motif.core.designsystem.icon.MotifIcons
import com.ssafy.motif.core.designsystem.theme.MotifTheme
import com.ssafy.motif.core.ui.challenges.ChallengeThumbnail
import com.ssafy.motif.core.ui.comment.ModalCommentBottomSheet
import com.ssafy.motif.core.ui.notice.NoticeCard
import com.ssafy.motif.feature.artistdetail.impl.artistdetail.component.ArtistDetailCard
import com.ssafy.motif.feature.artistdetail.impl.artistdetail.component.MotifEmptyContent
import com.ssafy.motif.core.ui.motif.MotifVideoSection

@Composable
fun ArtistDetailScreen(
    artistId: Long,
    modifier: Modifier = Modifier,
    viewModel: ArtistDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ArtistDetailScreen(
        uiState = uiState,
        onSubscribeClick = viewModel::toggleSubscribe,
        onAlarmClick = viewModel::toggleAlarm,
        onBackClick = viewModel::goBackScreen,
        onNoticeLikeClick = viewModel::toggleNoticeLike,
        onNoticeCommentClick = viewModel::onNoticeCommentClick,
        onTabClick = viewModel::selectTab,
        onCommentLikeClick = viewModel::onCommentLikeClick,
        onReplyClick = viewModel::onReplyClick,
        onCommentBackClick = viewModel::onCommentBackClick,
        onCommentSendClick = viewModel::onCommentSendClick,
        onChallengeClick = viewModel::onChallengeClick,
        onLoadMoreChallenges = viewModel::onLoadMoreChallenges,
        onCreateMotifClick = viewModel::onCreateMotifClick,
        modifier = modifier,
    )
}

@Composable
internal fun ArtistDetailScreen(
    uiState: ArtistDetailUiState,
    onSubscribeClick: () -> Unit,
    onAlarmClick: () -> Unit,
    onBackClick: () -> Unit,
    onNoticeLikeClick: (Long) -> Unit,
    onNoticeCommentClick: (Long) -> Unit,
    onTabClick: (ArtistDetailTab) -> Unit,
    onCommentLikeClick: (Long) -> Unit,
    onReplyClick: (Long) -> Unit,
    onCommentBackClick: () -> Unit,
    onCommentSendClick: (String) -> Unit,
    onChallengeClick: (Long) -> Unit,
    onLoadMoreChallenges: () -> Unit,
    onCreateMotifClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    var selectedNoticeId by rememberSaveable {
        mutableStateOf<Long?>(null)
    }

    LaunchedEffect(uiState.selectedTab) {
        listState.scrollToItem(0)
    }

    val challengeSuccess =
        uiState.challengeState as? ChallengeUiState.Success

    val canLoadMore =
        challengeSuccess != null &&
                uiState.selectedTab == ArtistDetailTab.Challenge &&
                challengeSuccess.hasNext &&
                challengeSuccess.appendState is ChallengeAppendUiState.Idle

    val shouldLoadMore by remember(
        listState,
        canLoadMore,
    ) {
        derivedStateOf {
            if (!canLoadMore) {
                return@derivedStateOf false
            }

            val layoutInfo = listState.layoutInfo
            val lastVisibleIndex =
                layoutInfo.visibleItemsInfo
                    .lastOrNull()
                    ?.index
                    ?: return@derivedStateOf false

            layoutInfo.totalItemsCount > 0 &&
                    lastVisibleIndex >=
                    layoutInfo.totalItemsCount - LOAD_MORE_THRESHOLD
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            onLoadMoreChallenges()
        }
    }

    val selectedNotice = selectedNoticeId?.let { noticeId ->
        uiState.notices.firstOrNull { notice ->
            notice.id == noticeId
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    bottom = 12.dp,
                ),
            ) {
                uiState.artistDetail?.let { artistDetail ->
                    if (artistDetail.hasMotifPass) {
                        val motif = uiState.representativeMotif

                        if (motif != null) {
                            item {
                                MotifVideoSection(
                                    videoId = motif.id.toString(),
                                    videoUrl = motif.videoUrl,
                                    thumbnailUrl = motif.thumbnailUrl,
                                    modifier = Modifier.padding(bottom = 12.dp),
                                )
                            }
                        } else {
                            item {
                                MotifEmptyContent(
                                    artistName = artistDetail.name,
                                    onCreateClick = onCreateMotifClick,
                                    modifier = Modifier.padding(
                                        bottom = 12.dp,
                                    ),
                                )
                            }
                        }
                    }

                    item {
                        ArtistDetailCard(
                            artistDetail = artistDetail,
                            isAlarmOn = artistDetail.isAlarmOn,
                            isSubscribed = artistDetail.isSubscribed,
                            onSubscribeClick = onSubscribeClick,
                            onAlarmClick = onAlarmClick,
                            modifier = if (artistDetail.hasMotifPass) {
                                Modifier
                            } else {
                                Modifier.padding(
                                    top = 12.dp,
                                )
                            },
                        )
                    }
                }

                stickyHeader {
                    TabRow(
                        selectedTabIndex = uiState.selectedTab.ordinal,
                        containerColor = MotifTheme.colors.background,
                        divider = {},
                        indicator = { tabPositions ->
                            if (uiState.selectedTab.ordinal < tabPositions.size) {
                                TabRowDefaults.Indicator(
                                    modifier = Modifier
                                        .tabIndicatorOffset(
                                            tabPositions[
                                                uiState.selectedTab.ordinal
                                            ],
                                        )
                                        .height(2.dp)
                                        .width(50.dp),
                                    color = MotifTheme.colors.pink,
                                )
                            }
                        },
                    ) {
                        ArtistDetailTab.entries.forEach { tab ->
                            Tab(
                                selected = uiState.selectedTab == tab,
                                onClick = {
                                    onTabClick(tab)
                                },
                                selectedContentColor = MotifTheme.colors.typo,
                                unselectedContentColor = MotifTheme.colors.gray400,
                            ) {
                                Text(
                                    text = tab.label,
                                    style = MotifTheme.typography.bodyR16,
                                    modifier = Modifier.padding(
                                        top = 24.dp,
                                        bottom = 26.dp,
                                    ),
                                )
                            }
                        }
                    }
                }

                when (uiState.selectedTab) {
                    ArtistDetailTab.Post -> {
                        items(
                            items = uiState.notices,
                            key = { notice ->
                                notice.id
                            },
                        ) { notice ->
                            NoticeCard(
                                notice = notice,
                                onArtistClick = {},
                                onLikeClick = onNoticeLikeClick,
                                onCommentClick = { noticeId ->
                                    onNoticeCommentClick(noticeId)
                                    selectedNoticeId = noticeId
                                },
                                modifier = Modifier.padding(
                                    start = 16.dp,
                                    end = 16.dp,
                                    bottom = 12.dp,
                                ),
                            )
                        }
                    }

                    ArtistDetailTab.Challenge -> {
                        when (val state = uiState.challengeState) {
                            ChallengeUiState.Loading -> {
                                item {
                                    ChallengeLoadingContent()
                                }
                            }

                            is ChallengeUiState.Success -> {
                                if (state.challenges.isEmpty()) {
                                    item {
                                        ChallengeEmptyContent()
                                    }
                                } else {
                                    items(
                                        items = state.challenges.chunked(3),
                                        key = { row ->
                                            row.first().id
                                        },
                                    ) { row ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(
                                                    bottom = 2.dp,
                                                ),
                                            horizontalArrangement =
                                                Arrangement.spacedBy(
                                                    2.dp,
                                                ),
                                        ) {
                                            row.forEach { challenge ->
                                                ChallengeThumbnail(
                                                    thumbnailUrl = challenge.thumbnailUrl,
                                                    viewCount = challenge.view,
                                                    onClick = {
                                                        onChallengeClick(
                                                            challenge.id,
                                                        )
                                                    },
                                                    modifier = Modifier.weight(
                                                        1f,
                                                    ),
                                                )
                                            }

                                            repeat(3 - row.size) {
                                                Spacer(
                                                    modifier = Modifier.weight(
                                                        1f,
                                                    ),
                                                )
                                            }
                                        }
                                    }

                                    if (
                                        state.appendState
                                                is ChallengeAppendUiState.Loading
                                    ) {
                                        item {
                                            ChallengeLoadingMore()
                                        }
                                    }
                                }
                            }

                            is ChallengeUiState.Error -> {
                                item {}
                            }
                        }
                    }
                }
            }

            IconButton(
                painter = painterResource(
                    MotifIcons.BarArrowLeft,
                ),
                onClick = onBackClick,
                size = 18.dp,
                colors = ModifIconButtonDefaults.iconButtonColors(
                    contentColor = MotifTheme.colors.typo,
                ),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(
                        x = 24.dp,
                        y = 24.dp,
                    ),
            )

            selectedNotice?.let { notice ->
                ModalCommentBottomSheet(
                    commentCount = notice.commentCount,
                    state = uiState.commentUiState,
                    onDismissRequest = {
                        selectedNoticeId = null
                    },
                    onLikeClick = onCommentLikeClick,
                    onReplyClick = onReplyClick,
                    onBackClick = onCommentBackClick,
                    onCommentSendClick = onCommentSendClick,
                )
            }
        }
    }
}

@Composable
private fun ChallengeLoadingMore(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                vertical = 24.dp,
            ),
        contentAlignment = Alignment.Center,
    ) {
        LoadingWheel(
            contentDescription = "불러오는 중...",
        )
    }
}

@Composable
private fun ChallengeEmptyContent(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                vertical = 80.dp,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "등록된 챌린지가 없습니다",
            style = MotifTheme.typography.bodyR14,
            color = MotifTheme.colors.gray400,
        )
    }
}

@Composable
private fun ChallengeLoadingContent(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                vertical = 80.dp,
            ),
        contentAlignment = Alignment.Center,
    ) {
        LoadingWheel(
            contentDescription = "불러오는 중...",
        )
    }
}

private const val LOAD_MORE_THRESHOLD = 3