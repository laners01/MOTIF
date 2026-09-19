package com.ssafy.motif.feature.motif.impl.assets

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.ssafy.motif.core.designsystem.component.LoadingWheel
import com.ssafy.motif.core.designsystem.component.Text
import com.ssafy.motif.core.designsystem.theme.MotifTheme
import com.ssafy.motif.core.model.home.Artist
import com.ssafy.motif.core.model.motif.Asset
import com.ssafy.motif.core.model.motif.AssetAvailability
import com.ssafy.motif.core.designsystem.R as DesignSystemR

/**
 * 모티프 서브 네비게이션의 에셋 목록 화면.
 *
 * @param contentPadding 서브 네비게이션 바에 가려지지 않도록 콘텐츠에 적용할 여백.
 */
@Composable
internal fun AssetListScreen(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    viewModel: AssetListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    AssetListScreen(
        uiState = uiState,
        contentPadding = contentPadding,
        onArtistClick = viewModel::selectArtist,
        onAssetClick = viewModel::selectAsset,
        onLoadMore = viewModel::loadMore,
        modifier = modifier,
    )
}

@Composable
private fun AssetListScreen(
    uiState: AssetListUiState,
    contentPadding: PaddingValues,
    onArtistClick: (Long?) -> Unit,
    onAssetClick: (Long) -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (uiState) {
        AssetListUiState.Loading -> {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .padding(contentPadding),
                contentAlignment = Alignment.Center,
            ) {
                LoadingWheel()
            }
        }

        is AssetListUiState.Success -> {
            AssetListContent(
                uiState = uiState,
                contentPadding = contentPadding,
                onArtistClick = onArtistClick,
                onAssetClick = onAssetClick,
                onLoadMore = onLoadMore,
                modifier = modifier,
            )
        }

        is AssetListUiState.Error -> {
            // TODO 에러 상태 UI. Mock 데이터는 실패하지 않으므로 이번 티켓에서는 빈 화면
            Box(modifier = modifier.fillMaxSize())
        }
    }
}

@Composable
private fun AssetListContent(
    uiState: AssetListUiState.Success,
    contentPadding: PaddingValues,
    onArtistClick: (Long?) -> Unit,
    onAssetClick: (Long) -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    // 필터가 바뀌면 목록을 최상단으로 되돌린다.
    LaunchedEffect(uiState.selectedArtistId) {
        listState.scrollToItem(0)
    }

    val canLoadMore = uiState.hasNext &&
            uiState.appendState is AssetListAppendUiState.Idle

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

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
    ) {
        item(key = "header") {
            AssetListHeader()
        }

        stickyHeader(key = "filter") {
            ArtistFilterRow(
                artists = uiState.artists,
                selectedArtistId = uiState.selectedArtistId,
                onArtistClick = onArtistClick,
                // sticky 상태에서 아래로 지나가는 카드가 비치지 않도록 배경을 깐다.
                modifier = Modifier.background(MotifTheme.colors.background),
            )
        }

        items(
            items = uiState.assets,
            key = { it.id },
        ) { asset ->
            AssetCard(
                asset = asset,
                onClick = onAssetClick,
            )
        }

        if (uiState.appendState is AssetListAppendUiState.Loading) {
            item(key = "footer") {
                AssetListLoadingFooter()
            }
        }
    }
}

@Composable
private fun AssetListHeader(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "MOTIF",
            color = MotifTheme.colors.white,
            style = MotifTheme.typography.rankR36.copy(
                fontSize = 28.sp,
                lineHeight = 32.sp,
            ),
        )
    }
}

@Composable
private fun ArtistFilterRow(
    artists: List<Artist>,
    selectedArtistId: Long?, // ALL인 경우 null
    onArtistClick: (Long?) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        // ALL 칩은 라벨이 없어 세로 중앙 정렬하면 아바타보다 아래로 내려간다. 원들의 윗변을 맞춘다.
        verticalAlignment = Alignment.Top,
    ) {
        item(key = "all") {
            AllFilterChip(
                selected = selectedArtistId == null,
                onClick = { onArtistClick(null) },
            )
        }

        items(artists, key = { it.id }) { artist ->
            ArtistFilterItem(
                artist = artist,
                selected = artist.id == selectedArtistId,
                onClick = { onArtistClick(artist.id) },
            )
        }
    }
}

@Composable
private fun AllFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor by animateColorAsState(
        targetValue = if (selected) MotifTheme.colors.pink else Color.Transparent,
        label = "all chip border",
    )

    Box(
        modifier = modifier
            .size(AllChipSize)
            .clip(CircleShape)
            .background(MotifTheme.colors.black)
            .border(
                width = FilterBorderWidth,
                color = borderColor,
                shape = CircleShape,
            )
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "ALL",
            color = MotifTheme.colors.white,
            style = MotifTheme.typography.bodySB16,
        )
    }
}

@Composable
private fun ArtistFilterItem(
    artist: Artist,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor by animateColorAsState(
        targetValue = if (selected) MotifTheme.colors.pink else Color.Transparent,
        label = "artist filter border",
    )

    Column(
        modifier = modifier
            .width(ArtistItemSize)
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AsyncImage(
            model = artist.profileImageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(ArtistItemSize)
                .clip(CircleShape)
                .border(
                    width = FilterBorderWidth,
                    color = borderColor,
                    shape = CircleShape,
                ),
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = artist.name,
            color = MotifTheme.colors.typo,
            style = MotifTheme.typography.captionM12,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AssetCard(
    asset: Asset,
    onClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(role = Role.Button) { onClick(asset.id) },
    ) {
        AsyncImage(
            model = asset.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f),
        )

        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                text = asset.title,
                color = MotifTheme.colors.typo,
                style = MotifTheme.typography.bodyR16,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = asset.artist.displayNameWithGroup(),
                color = MotifTheme.colors.gray500,
                style = MotifTheme.typography.bodyR12,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun AssetListLoadingFooter(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        LoadingWheel()
    }
}

// TODO core:ui의 MotifCard에 같은 함수가 private으로 있음. 공유가 필요해지면 Artist 확장으로 승격
private fun Artist.displayNameWithGroup(): String =
    groupName?.takeIf { it.isNotBlank() }?.let { "$name · $it" } ?: name

private const val LOAD_MORE_THRESHOLD = 2

private val AllChipSize = 70.dp
private val ArtistItemSize = 70.dp
private val FilterBorderWidth = 2.dp

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun AssetListScreenPreview() {
    val artists = previewArtists()

    MotifTheme {
        AssetListScreen(
            uiState = AssetListUiState.Success(
                assets = previewAssets(artists),
                artists = artists,
                selectedArtistId = null,
                hasNext = true,
                appendState = AssetListAppendUiState.Loading,
            ),
            contentPadding = PaddingValues(0.dp),
            onArtistClick = {},
            onAssetClick = {},
            onLoadMore = {},
        )
    }
}

/**
 * 실기기 Run Preview용. 필터를 누르면 선택과 목록이 실제로 바뀐다.
 * 추가 로드는 ViewModel이 담당하므로 여기서는 동작하지 않는다.
 */
@Preview(name = "Interactive", showBackground = true, heightDp = 900)
@Composable
private fun AssetListScreenInteractivePreview() {
    val artists = previewArtists()
    val assets = previewAssets(artists)

    var uiState by remember {
        mutableStateOf(
            AssetListUiState.Success(
                assets = assets,
                artists = artists,
                selectedArtistId = null,
                hasNext = false,
            ),
        )
    }

    MotifTheme {
        AssetListScreen(
            uiState = uiState,
            contentPadding = PaddingValues(0.dp),
            onArtistClick = { artistId ->
                uiState = uiState.copy(
                    selectedArtistId = artistId,
                    assets = if (artistId == null) assets else assets.filter { it.artist.id == artistId },
                )
            },
            onAssetClick = {},
            onLoadMore = {},
        )
    }
}

@Preview(name = "Loading", showBackground = true, heightDp = 400)
@Composable
private fun AssetListScreenLoadingPreview() {
    MotifTheme {
        AssetListScreen(
            uiState = AssetListUiState.Loading,
            contentPadding = PaddingValues(0.dp),
            onArtistClick = {},
            onAssetClick = {},
            onLoadMore = {},
        )
    }
}

@Preview(name = "Long title", showBackground = true)
@Composable
private fun AssetCardLongTitlePreview() {
    MotifTheme {
        AssetCard(
            asset = Asset(
                id = 1L,
                thumbnailUrl = previewImageUrl(DesignSystemR.drawable.img_test_motif),
                title = "연습실에서 카리나와 함께 마지막 동선 체크까지 완벽하게 맞춰보는 리허설 타임 🕺",
                artist = previewArtists().first(),
                availability = AssetAvailability.PURCHASED,
            ),
            onClick = {},
        )
    }
}

/**
 * Preview 렌더러는 앱 패키지의 리소스를 직접 못 읽으므로, 현재 패키지 기준 android.resource URL로 만든다.
 */
@Composable
private fun previewImageUrl(resId: Int): String {
    val context = LocalContext.current
    return "android.resource://${context.packageName}/$resId"
}

@Composable
private fun previewArtists(): List<Artist> {
    val profileUrl = previewImageUrl(DesignSystemR.drawable.img_profile)

    return listOf(
        Artist(id = 1L, name = "YEJI", profileImageUrl = profileUrl, groupName = "ITZY"),
        Artist(id = 2L, name = "카리나", profileImageUrl = profileUrl, groupName = "aespa"),
        Artist(id = 3L, name = "지수", profileImageUrl = profileUrl, groupName = "BLACKPINK"),
        Artist(id = 4L, name = "이채영", profileImageUrl = profileUrl, groupName = "fromis_9"),
        Artist(id = 5L, name = "오해원", profileImageUrl = profileUrl, groupName = "NMIXX"),
    )
}

@Composable
private fun previewAssets(artists: List<Artist>): List<Asset> {
    val thumbnailUrl = previewImageUrl(DesignSystemR.drawable.img_test_motif)

    val titles = listOf(
        "카리나와 함께 만드는 핑거하트 모먼트 💖",
        "카페 맞은편 자리에 앉아 예지와 함께 여유로운 순간을 ☕",
        "지수와 함께하는 봄날의 피크닉 🌸",
        "무대 뒤에서 이채영과 나누는 비하인드 토크 🎤",
        "오해원과 함께 걷는 한강 노을 산책 🌇",
        "연습실에서 카리나와 함께 마지막 동선 체크까지 완벽하게 맞춰보는 리허설 타임 🕺",
        "지수의 플레이리스트로 시작하는 아침 ☀️",
        "예지와 함께 찍는 즉석 사진 📸",
    )

    return titles.mapIndexed { index, title ->
        Asset(
            id = index + 1L,
            thumbnailUrl = thumbnailUrl,
            title = title,
            artist = artists[index % artists.size],
            availability = AssetAvailability.SUBSCRIPTION,
        )
    }
}
