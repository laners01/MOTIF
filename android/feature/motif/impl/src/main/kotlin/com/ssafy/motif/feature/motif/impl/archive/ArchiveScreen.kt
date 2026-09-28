package com.ssafy.motif.feature.motif.impl.archive

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.motif.core.designsystem.component.LoadingWheel
import com.ssafy.motif.core.designsystem.component.Text
import com.ssafy.motif.core.designsystem.theme.MotifTheme
import com.ssafy.motif.feature.motif.impl.R
import com.ssafy.motif.feature.motif.impl.archive.component.ArchivePlayer
import com.ssafy.motif.feature.motif.impl.archive.component.SavedMotifItem
import com.ssafy.motif.feature.motif.impl.archive.component.WithArtistFilter
import com.ssafy.motif.feature.motif.impl.component.LoadingFooter
import com.ssafy.motif.feature.motif.impl.component.MotifEmptyContent

@Composable
internal fun ArchiveScreen(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    viewModel: ArchiveViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ArchiveScreen(
        uiState = uiState,
        contentPadding = contentPadding,
        onArtistClick = viewModel::selectArtist,
        onMotifClick = viewModel::selectMotif,
        onRepresentativeClick = viewModel::toggleRepresentative,
        onLoadMore = viewModel::loadMore,
        modifier = modifier,
    )
}

@Composable
private fun ArchiveScreen(
    uiState: ArchiveUiState,
    contentPadding: PaddingValues,
    onArtistClick: (Long) -> Unit,
    onMotifClick: (Long) -> Unit,
    onRepresentativeClick: (Long) -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (uiState) {
        ArchiveUiState.Loading -> {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .padding(contentPadding),
                contentAlignment = Alignment.Center,
            ) {
                LoadingWheel()
            }
        }

        is ArchiveUiState.Success -> {
            if (uiState.isGlobalEmpty) {
                ArchiveEmptyContent(
                    contentPadding = contentPadding,
                    modifier = modifier,
                )
            } else {
                ArchiveContent(
                    uiState = uiState,
                    contentPadding = contentPadding,
                    onArtistClick = onArtistClick,
                    onMotifClick = onMotifClick,
                    onRepresentativeClick = onRepresentativeClick,
                    onLoadMore = onLoadMore,
                    modifier = modifier,
                )
            }
        }

        is ArchiveUiState.Error -> {
            Box(
                modifier = modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun ArchiveEmptyContent(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(
                bottom = contentPadding.calculateBottomPadding(),
            ),
    ) {
        MotifEmptyContent(
            title = stringResource(
                R.string.feature_motif_impl_archive_empty_title,
            ),
            description = stringResource(
                R.string.feature_motif_impl_archive_empty_description,
            ),
        )
    }
}

@Composable
private fun ArchiveContent(
    uiState: ArchiveUiState.Success,
    contentPadding: PaddingValues,
    onArtistClick: (Long) -> Unit,
    onMotifClick: (Long) -> Unit,
    onRepresentativeClick: (Long) -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    val canLoadMore =
        uiState.hasNext &&
                uiState.appendState is ArchiveAppendUiState.Idle

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
            onLoadMore()
        }
    }

    Column(
        modifier = modifier.fillMaxSize(),
    ) {
        uiState.selectedMotif?.let { motif ->
            ArchivePlayer(
                motif = motif,
            )
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
        ) {
            item(
                key = "filter",
            ) {
                WithArtistFilter(
                    artists = uiState.artists,
                    selectedArtistId = uiState.selectedArtistId,
                    onArtistClick = onArtistClick,
                    modifier = Modifier.padding(
                        start = 16.dp,
                        end = 16.dp,
                        top = FilterTopSpacing,
                        bottom = FilterBottomSpacing,
                    ),
                )
            }

            if (uiState.motifs.isEmpty()) {
                item(
                    key = "filtered-empty",
                ) {
                    Text(
                        text = stringResource(
                            R.string.feature_motif_impl_archive_filter_empty,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                vertical = 48.dp,
                            ),
                        color = MotifTheme.colors.gray500,
                        style = MotifTheme.typography.bodyR14,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            items(
                items = uiState.motifs,
                key = { motif ->
                    motif.id
                },
            ) { motif ->
                SavedMotifItem(
                    motif = motif,
                    onClick = onMotifClick,
                    onRepresentativeClick = onRepresentativeClick,
                    isRepresentativeUpdating =
                        uiState.updatingRepresentativeMotifId ==
                                motif.id,
                    modifier = Modifier.padding(
                        horizontal = 16.dp,
                        vertical = ItemVerticalSpacing,
                    ),
                )
            }

            if (
                uiState.appendState is
                        ArchiveAppendUiState.Loading
            ) {
                item(
                    key = "footer",
                ) {
                    LoadingFooter()
                }
            }
        }
    }
}

private val ArchiveUiState.Success.isGlobalEmpty: Boolean
    get() =
        motifs.isEmpty() &&
                artists.isEmpty() &&
                selectedArtistId == null &&
                selectedMotif == null

private const val LOAD_MORE_THRESHOLD = 2

private val FilterTopSpacing = 8.dp
private val FilterBottomSpacing = 12.dp
private val ItemVerticalSpacing = 8.dp

@Preview(
    name = "Empty",
    showBackground = true,
    heightDp = 900,
)
@Composable
private fun ArchiveScreenEmptyPreview() {
    MotifTheme {
        ArchiveScreen(
            uiState = ArchiveUiState.Success(
                motifs = emptyList(),
                artists = emptyList(),
                selectedArtistId = null,
                selectedMotif = null,
                nextCursor = null,
                hasNext = false,
            ),
            contentPadding = PaddingValues(
                0.dp,
            ),
            onArtistClick = {},
            onMotifClick = {},
            onRepresentativeClick = {},
            onLoadMore = {},
        )
    }
}

@Preview(
    showBackground = true,
    heightDp = 900,
)
@Composable
private fun ArchiveScreenPreview() {
    val motifs =
        ArchiveMockData
            .savedMotifs
            .take(6)

    MotifTheme {
        ArchiveScreen(
            uiState = ArchiveUiState.Success(
                motifs = motifs,
                artists = motifs
                    .map { motif ->
                        motif.artist
                    }
                    .distinctBy { artist ->
                        artist.id
                    },
                selectedArtistId = null,
                selectedMotif = motifs.first(),
                nextCursor = "next",
                hasNext = true,
                appendState = ArchiveAppendUiState.Loading,
            ),
            contentPadding = PaddingValues(
                0.dp,
            ),
            onArtistClick = {},
            onMotifClick = {},
            onRepresentativeClick = {},
            onLoadMore = {},
        )
    }
}

@Preview(
    name = "Interactive",
    showBackground = true,
    heightDp = 900,
)
@Composable
private fun ArchiveScreenInteractivePreview() {
    val allMotifs =
        ArchiveMockData
            .savedMotifs
            .take(8)

    val artists =
        allMotifs
            .map { motif ->
                motif.artist
            }
            .distinctBy { artist ->
                artist.id
            }

    var motifs by remember {
        mutableStateOf(
            allMotifs,
        )
    }

    var selectedArtistId by remember {
        mutableStateOf<Long?>(
            null,
        )
    }

    var selectedMotifId by remember {
        mutableStateOf(
            allMotifs.first().id,
        )
    }

    val visibleMotifs =
        motifs.filter { motif ->
            selectedArtistId == null ||
                    motif.artist.id ==
                    selectedArtistId
        }

    MotifTheme {
        ArchiveScreen(
            uiState = ArchiveUiState.Success(
                motifs = visibleMotifs,
                artists = artists,
                selectedArtistId = selectedArtistId,
                selectedMotif = motifs
                    .firstOrNull { motif ->
                        motif.id ==
                                selectedMotifId
                    },
                nextCursor = null,
                hasNext = false,
            ),
            contentPadding = PaddingValues(
                0.dp,
            ),
            onArtistClick = { artistId ->
                selectedArtistId =
                    if (
                        selectedArtistId ==
                        artistId
                    ) {
                        null
                    } else {
                        artistId
                    }
            },
            onMotifClick = {
                selectedMotifId = it
            },
            onRepresentativeClick = { motifId ->
                val makeRepresentative =
                    !motifs
                        .first { motif ->
                            motif.id ==
                                    motifId
                        }
                        .isRepresentative

                motifs = motifs.map { motif ->
                    motif.copy(
                        isRepresentative =
                            makeRepresentative &&
                                    motif.id ==
                                    motifId,
                    )
                }
            },
            onLoadMore = {},
        )
    }
}

@Preview(
    name = "Loading",
    showBackground = true,
    heightDp = 400,
)
@Composable
private fun ArchiveScreenLoadingPreview() {
    MotifTheme {
        ArchiveScreen(
            uiState = ArchiveUiState.Loading,
            contentPadding = PaddingValues(
                0.dp,
            ),
            onArtistClick = {},
            onMotifClick = {},
            onRepresentativeClick = {},
            onLoadMore = {},
        )
    }
}