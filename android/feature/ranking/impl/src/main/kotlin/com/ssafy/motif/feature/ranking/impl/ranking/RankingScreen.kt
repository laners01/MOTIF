package com.ssafy.motif.feature.ranking.impl.ranking

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.ssafy.motif.core.designsystem.component.Icon
import com.ssafy.motif.core.designsystem.component.IconButton
import com.ssafy.motif.core.designsystem.component.LoadingWheel
import com.ssafy.motif.core.designsystem.component.ModifIconButtonDefaults
import com.ssafy.motif.core.designsystem.component.Surface
import com.ssafy.motif.core.designsystem.component.Text
import com.ssafy.motif.core.designsystem.icon.MotifIcons
import com.ssafy.motif.core.designsystem.theme.MotifTheme
import com.ssafy.motif.core.model.challenge.ChallengeRank
import kotlin.math.absoluteValue

@Composable
fun RankingScreen(
    onBackClick: () -> Unit,
    onArtistClick: (Long) -> Unit,
    onPlayClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RankingViewModel = hiltViewModel(),
) {
    val uiState by
    viewModel
        .uiState
        .collectAsStateWithLifecycle()

    RankingScreen(
        uiState = uiState,
        onArtistClick = onArtistClick,
        onQueryChange = viewModel::updateQuery,
        onRemoveClick = viewModel::removeQuery,
        onBackClick = onBackClick,
        onPlayClick = onPlayClick,
        onLoadMore = viewModel::loadMore,
        onRetry = viewModel::retry,
        modifier = modifier,
    )
}

@Composable
internal fun RankingScreen(
    uiState: RankingUiState,
    onPlayClick: (Long) -> Unit,
    onArtistClick: (Long) -> Unit,
    onQueryChange: (String) -> Unit,
    onRemoveClick: () -> Unit,
    onBackClick: () -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val gridState =
        rememberLazyGridState()

    val shouldLoadMore by
    remember(
        gridState,
        uiState.hasNext,
        uiState.isLoadingMore,
        uiState.filteredRanking.size,
    ) {
        derivedStateOf {
            if (
                !uiState.hasNext ||
                uiState.isLoadingMore
            ) {
                return@derivedStateOf false
            }

            val layoutInfo =
                gridState.layoutInfo

            val lastVisibleIndex =
                layoutInfo
                    .visibleItemsInfo
                    .lastOrNull()
                    ?.index
                    ?: return@derivedStateOf false

            layoutInfo.totalItemsCount > 0 &&
                    lastVisibleIndex >=
                    layoutInfo.totalItemsCount -
                    LOAD_MORE_THRESHOLD
        }
    }

    LaunchedEffect(
        shouldLoadMore,
    ) {
        if (shouldLoadMore) {
            onLoadMore()
        }
    }

    Column(
        modifier =
            modifier.fillMaxSize(),
    ) {
        Row(
            modifier =
                Modifier.padding(
                    start = 20.dp,
                    top = 23.dp,
                    bottom = 23.dp,
                ),
            verticalAlignment =
                Alignment.CenterVertically,
            horizontalArrangement =
                Arrangement.spacedBy(
                    20.dp,
                ),
        ) {
            IconButton(
                painter =
                    painterResource(
                        MotifIcons.BarArrowLeft,
                    ),
                onClick =
                    onBackClick,
                size =
                    18.dp,
                colors =
                    ModifIconButtonDefaults
                        .iconButtonColors(
                            contentColor =
                                MotifTheme.colors.typo,
                        ),
            )

            Text(
                text = "Who's on",
                style =
                    MotifTheme
                        .typography
                        .bodySB16
                        .copy(
                            lineHeight = 19.sp,
                        ),
                color =
                    MotifTheme.colors.typo,
                maxLines = 1,
                overflow =
                    TextOverflow.Visible,
            )
        }

        ArtistSearchBar(
            query =
                uiState.query,
            onQueryChange =
                onQueryChange,
            onRemoveClick =
                onRemoveClick,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = 20.dp,
                    ),
        )

        Box(
            modifier =
                Modifier.weight(
                    1f,
                ),
        ) {
            when {
                uiState.isLoading -> {
                    LoadingContent(
                        modifier =
                            Modifier.align(
                                Alignment.Center,
                            ),
                    )
                }

                uiState.isError &&
                        uiState.ranking.isEmpty() -> {
                    ErrorContent(
                        onRetryClick =
                            onRetry,
                        modifier =
                            Modifier.align(
                                Alignment.Center,
                            ),
                    )
                }

                uiState.ranking.isEmpty() -> {
                    EmptyContent(
                        text =
                            "랭킹 정보가 없습니다",
                        modifier =
                            Modifier.align(
                                Alignment.Center,
                            ),
                    )
                }

                else -> {
                    LazyVerticalGrid(
                        state =
                            gridState,
                        columns =
                            GridCells.Fixed(
                                1,
                            ),
                        contentPadding =
                            PaddingValues(
                                top = 24.dp,
                                start = 20.dp,
                                end = 20.dp,
                                bottom = 20.dp,
                            ),
                        verticalArrangement =
                            Arrangement.spacedBy(
                                20.dp,
                            ),
                        modifier =
                            Modifier.fillMaxSize(),
                    ) {
                        items(
                            items =
                                uiState.filteredRanking,
                            key = { ranking ->
                                ranking.artist.id
                            },
                        ) { ranking ->
                            ArtistRankCard(
                                challenge =
                                    ranking,
                                onArtistClick = {
                                    onArtistClick(
                                        ranking.artist.id,
                                    )
                                },
                                onPlayClick =
                                    ranking.challengeId
                                        ?.let { challengeId ->
                                            {
                                                onPlayClick(
                                                    challengeId,
                                                )
                                            }
                                        },
                            )
                        }

                        if (
                            uiState.isLoadingMore
                        ) {
                            item {
                                Box(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(
                                                vertical = 20.dp,
                                            ),
                                    contentAlignment =
                                        Alignment.Center,
                                ) {
                                    LoadingWheel(
                                        contentDescription =
                                            "랭킹을 더 불러오는 중",
                                    )
                                }
                            }
                        }
                    }

                    if (
                        uiState.query.isNotBlank() &&
                        uiState.filteredRanking.isEmpty()
                    ) {
                        EmptyContent(
                            text =
                                "검색 결과가 없습니다",
                            modifier =
                                Modifier.align(
                                    Alignment.Center,
                                ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun ArtistRankCard(
    challenge: ChallengeRank,
    onArtistClick: () -> Unit,
    onPlayClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier.fillMaxWidth(),
        verticalAlignment =
            Alignment.CenterVertically,
        horizontalArrangement =
            Arrangement.spacedBy(
                12.dp,
            ),
    ) {
        RankContent(
            rank =
                challenge.rank,
            rankDelta =
                challenge.rankDelta,
        )

        AsyncImage(
            model =
                challenge
                    .artist
                    .profileImageUrl,
            contentDescription =
                challenge.artist.name,
            contentScale =
                ContentScale.Crop,
            placeholder =
                painterResource(
                    com.ssafy.motif.core.designsystem.R.drawable.profile_square,
                ),
            error =
                painterResource(
                    com.ssafy.motif.core.designsystem.R.drawable.profile_square,
                ),
            fallback =
                painterResource(
                    com.ssafy.motif.core.designsystem.R.drawable.profile_square,
                ),
            modifier =
                Modifier
                    .width(
                        48.dp,
                    )
                    .aspectRatio(
                        1f,
                    )
                    .clip(
                        RoundedCornerShape(
                            4.dp,
                        ),
                    )
                    .clickable(
                        onClick =
                            onArtistClick,
                    ),
        )

        Row(
            verticalAlignment =
                Alignment.CenterVertically,
            horizontalArrangement =
                Arrangement.spacedBy(
                    8.dp,
                ),
        ) {
            Text(
                text =
                    challenge
                        .artist
                        .name,
                style =
                    MotifTheme
                        .typography
                        .bodySB16,
                color =
                    MotifTheme
                        .colors
                        .typo,
                maxLines = 1,
                overflow =
                    TextOverflow.Ellipsis,
            )

            challenge
                .artist
                .groupName
                ?.takeIf {
                    it.isNotBlank()
                }
                ?.let { groupName ->
                    Text(
                        text =
                            groupName,
                        style =
                            MotifTheme
                                .typography
                                .captionSB10,
                        color =
                            MotifTheme
                                .colors
                                .gray500,
                        maxLines = 1,
                        overflow =
                            TextOverflow.Ellipsis,
                    )
                }
        }

        Text(
            text =
                challenge
                    .song
                    ?.title
                    .orEmpty(),
            style =
                MotifTheme
                    .typography
                    .bodySB16,
            color =
                MotifTheme
                    .colors
                    .typo,
            maxLines = 1,
            overflow =
                TextOverflow.Ellipsis,
            textAlign =
                TextAlign.End,
            modifier =
                Modifier.weight(
                    1f,
                ),
        )

        if (onPlayClick != null) {
            Box(
                modifier =
                    Modifier
                        .size(
                            20.dp,
                        )
                        .clickable(
                            onClick =
                                onPlayClick,
                        ),
                contentAlignment =
                    Alignment.Center,
            ) {
                Icon(
                    painter =
                        painterResource(
                            MotifIcons.PlayOutline,
                        ),
                    contentDescription =
                        "챌린지 재생",
                    tint =
                        MotifTheme
                            .colors
                            .pink,
                    modifier =
                        Modifier
                            .height(
                                18.dp,
                            )
                            .width(
                                14.dp,
                            ),
                )
            }
        } else {
            Spacer(
                modifier =
                    Modifier.width(
                        20.dp,
                    ),
            )
        }
    }
}

@Composable
private fun RankContent(
    rank: Long,
    rankDelta: Long?,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment =
            Alignment.CenterHorizontally,
        verticalArrangement =
            Arrangement.spacedBy(
                7.dp,
            ),
        modifier =
            modifier.width(
                24.dp,
            ),
    ) {
        Text(
            text =
                rank.toString(),
            style =
                MotifTheme
                    .typography
                    .titleSB20,
            color =
                MotifTheme
                    .colors
                    .typo,
            maxLines = 1,
            overflow =
                TextOverflow.Visible,
        )

        if (rankDelta != null) {
            Row(
                verticalAlignment =
                    Alignment.CenterVertically,
                horizontalArrangement =
                    Arrangement.spacedBy(
                        5.dp,
                    ),
            ) {
                Icon(
                    painter =
                        when {
                            rankDelta > 0L -> {
                                painterResource(
                                    MotifIcons.RankingUpper,
                                )
                            }

                            rankDelta < 0L -> {
                                painterResource(
                                    MotifIcons.RankingLower,
                                )
                            }

                            else -> {
                                painterResource(
                                    MotifIcons.RankingUnchanged,
                                )
                            }
                        },
                    contentDescription = null,
                    tint =
                        if (
                            rankDelta > 0L
                        ) {
                            MotifTheme
                                .colors
                                .pink
                        } else {
                            MotifTheme
                                .colors
                                .gray500
                        },
                    modifier =
                        if (
                            rankDelta != 0L
                        ) {
                            Modifier
                                .height(
                                    7.dp,
                                )
                                .width(
                                    6.dp,
                                )
                        } else {
                            Modifier
                                .height(
                                    2.dp,
                                )
                                .width(
                                    12.dp,
                                )
                        },
                )

                if (
                    rankDelta != 0L
                ) {
                    Text(
                        text =
                            rankDelta
                                .absoluteValue
                                .toString(),
                        style =
                            MotifTheme
                                .typography
                                .captionSB8,
                        color =
                            MotifTheme
                                .colors
                                .typo,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun ArtistSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onRemoveClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier =
            modifier,
        color =
            MotifTheme
                .colors
                .gray700,
        contentPadding =
            PaddingValues(
                horizontal = 7.dp,
                vertical = 12.dp,
            ),
    ) {
        Row(
            verticalAlignment =
                Alignment.CenterVertically,
        ) {
            Icon(
                painter =
                    painterResource(
                        MotifIcons.Search,
                    ),
                contentDescription = null,
                tint =
                    MotifTheme
                        .colors
                        .typo,
                modifier =
                    Modifier.size(
                        20.dp,
                    ),
            )

            BasicTextField(
                value =
                    query,
                onValueChange =
                    onQueryChange,
                singleLine =
                    true,
                textStyle =
                    MotifTheme
                        .typography
                        .bodyR14
                        .copy(
                            color =
                                MotifTheme
                                    .colors
                                    .typo,
                        ),
                cursorBrush =
                    SolidColor(
                        MotifTheme
                            .colors
                            .typo,
                    ),
                modifier =
                    Modifier
                        .weight(
                            1f,
                        )
                        .padding(
                            start = 8.dp,
                        ),
                decorationBox = {
                        innerTextField,
                    ->
                    if (
                        query.isEmpty()
                    ) {
                        Text(
                            text =
                                "함께하고 싶은 멤버나 그룹을 찾아보세요",
                            style =
                                MotifTheme
                                    .typography
                                    .bodyR16,
                            color =
                                MotifTheme
                                    .colors
                                    .typo,
                        )
                    }

                    innerTextField()
                },
            )

            if (
                query.isNotEmpty()
            ) {
                IconButton(
                    painter =
                        painterResource(
                            MotifIcons.Close,
                        ),
                    onClick =
                        onRemoveClick,
                    colors =
                        ModifIconButtonDefaults
                            .iconButtonColors(
                                contentColor =
                                    MotifTheme
                                        .colors
                                        .typo,
                            ),
                    modifier =
                        Modifier
                            .size(
                                20.dp,
                            )
                            .padding(
                                end = 5.dp,
                            ),
                )
            }
        }
    }
}

@Composable
private fun LoadingContent(
    modifier: Modifier = Modifier,
) {
    LoadingWheel(
        contentDescription =
            "랭킹 불러오는 중",
        modifier =
            modifier,
    )
}

@Composable
private fun EmptyContent(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text =
            text,
        style =
            MotifTheme
                .typography
                .bodyR14,
        color =
            MotifTheme
                .colors
                .gray400,
        modifier =
            modifier,
    )
}

@Composable
private fun ErrorContent(
    onRetryClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier,
        horizontalAlignment =
            Alignment.CenterHorizontally,
        verticalArrangement =
            Arrangement.spacedBy(
                12.dp,
            ),
    ) {
        Text(
            text =
                "랭킹을 불러오지 못했습니다",
            style =
                MotifTheme
                    .typography
                    .bodyR14,
            color =
                MotifTheme
                    .colors
                    .gray400,
        )

        Text(
            text =
                "다시 시도",
            style =
                MotifTheme
                    .typography
                    .bodySB14,
            color =
                MotifTheme
                    .colors
                    .pink,
            modifier =
                Modifier.clickable(
                    onClick =
                        onRetryClick,
                ),
        )
    }
}

private const val LOAD_MORE_THRESHOLD = 3