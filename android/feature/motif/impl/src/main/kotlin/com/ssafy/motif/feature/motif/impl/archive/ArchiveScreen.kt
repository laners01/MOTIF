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

/**
 * 모티프 서브 네비게이션의 보관함 화면.
 *
 * @param contentPadding 서브 네비게이션 바에 가려지지 않도록 콘텐츠에 적용할 여백.
 */
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
            ArchiveContent(
                uiState = uiState,
                contentPadding = contentPadding,
                onArtistClick = onArtistClick,
                onMotifClick = onMotifClick,
                onLoadMore = onLoadMore,
                modifier = modifier,
            )
        }

        is ArchiveUiState.Error -> {
            // TODO 에러 상태 UI. Mock 데이터는 실패하지 않으므로 이번 티켓에서는 빈 화면
            Box(modifier = modifier.fillMaxSize())
        }
    }
}

@Composable
private fun ArchiveContent(
    uiState: ArchiveUiState.Success,
    contentPadding: PaddingValues,
    onArtistClick: (Long) -> Unit,
    onMotifClick: (Long) -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    val canLoadMore = uiState.hasNext &&
            uiState.appendState is ArchiveAppendUiState.Idle

    val shouldLoadMore by remember(listState, canLoadMore) {
        derivedStateOf {
            if (!canLoadMore) return@derivedStateOf false

            val layoutInfo = listState.layoutInfo
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index
                ?: return@derivedStateOf false

            layoutInfo.totalItemsCount > 0 &&
                    lastVisible >= layoutInfo.totalItemsCount - LOAD_MORE_THRESHOLD
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            onLoadMore()
        }
    }

    Column(
        modifier = modifier.fillMaxSize(),
    ) {
        // 재생 영역은 목록 밖에 두어 스크롤해도 화면 상단에 고정된다.
        uiState.selectedMotif?.let { motif ->
            ArchivePlayer(motif = motif)
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
        ) {
            item(key = "filter") {
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
                item(key = "empty") {
                    Text(
                        text = stringResource(R.string.feature_motif_impl_archive_empty),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        color = MotifTheme.colors.gray500,
                        style = MotifTheme.typography.bodyR14,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            items(
                items = uiState.motifs,
                key = { it.id },
            ) { motif ->
                SavedMotifItem(
                    motif = motif,
                    onClick = onMotifClick,
                    modifier = Modifier.padding(
                        horizontal = 16.dp,
                        vertical = ItemVerticalSpacing,
                    ),
                )
            }

            if (uiState.appendState is ArchiveAppendUiState.Loading) {
                item(key = "footer") {
                    LoadingFooter()
                }
            }
        }
    }
}

private const val LOAD_MORE_THRESHOLD = 2

private val FilterTopSpacing = 8.dp
private val FilterBottomSpacing = 12.dp
private val ItemVerticalSpacing = 8.dp

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun ArchiveScreenPreview() {
    val motifs = ArchiveMockData.savedMotifs.take(6)

    MotifTheme {
        ArchiveScreen(
            uiState = ArchiveUiState.Success(
                motifs = motifs,
                artists = motifs.map { it.artist }.distinctBy { it.id },
                selectedArtistId = null,
                selectedMotif = motifs.first(),
                hasNext = true,
                appendState = ArchiveAppendUiState.Loading,
            ),
            contentPadding = PaddingValues(0.dp),
            onArtistClick = {},
            onMotifClick = {},
            onLoadMore = {},
        )
    }
}

/**
 * 실기기 Run Preview용. 항목 선택·필터가 실제로 바뀐다.
 * 영상 재생과 추가 로드는 ViewModel이 담당하므로 여기서는 동작하지 않는다.
 */
@Preview(name = "Interactive", showBackground = true, heightDp = 900)
@Composable
private fun ArchiveScreenInteractivePreview() {
    val motifs = ArchiveMockData.savedMotifs.take(8)
    val artists = motifs.map { it.artist }.distinctBy { it.id }

    var selectedArtistId by remember { mutableStateOf<Long?>(null) }
    var selectedMotifId by remember { mutableStateOf(motifs.first().id) }

    val visibleMotifs = motifs.filter { selectedArtistId == null || it.artist.id == selectedArtistId }

    MotifTheme {
        ArchiveScreen(
            uiState = ArchiveUiState.Success(
                motifs = visibleMotifs,
                artists = artists,
                selectedArtistId = selectedArtistId,
                selectedMotif = motifs.firstOrNull { it.id == selectedMotifId },
                hasNext = false,
            ),
            contentPadding = PaddingValues(0.dp),
            onArtistClick = { artistId ->
                selectedArtistId = if (selectedArtistId == artistId) null else artistId
            },
            onMotifClick = { selectedMotifId = it },
            onLoadMore = {},
        )
    }
}

@Preview(name = "Loading", showBackground = true, heightDp = 400)
@Composable
private fun ArchiveScreenLoadingPreview() {
    MotifTheme {
        ArchiveScreen(
            uiState = ArchiveUiState.Loading,
            contentPadding = PaddingValues(0.dp),
            onArtistClick = {},
            onMotifClick = {},
            onLoadMore = {},
        )
    }
}
