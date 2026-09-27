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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.ssafy.motif.core.designsystem.component.LoadingWheel
import com.ssafy.motif.core.designsystem.component.Text
import com.ssafy.motif.core.designsystem.theme.MotifTheme
import com.ssafy.motif.core.model.artist.Artist
import com.ssafy.motif.core.model.motif.Asset
import com.ssafy.motif.core.model.motif.AssetAvailability
import com.ssafy.motif.feature.motif.impl.R
import com.ssafy.motif.feature.motif.impl.component.ArtistFilterItem
import com.ssafy.motif.feature.motif.impl.component.ArtistFilterItemDefaults
import com.ssafy.motif.feature.motif.impl.component.MotifEmptyContent
import com.ssafy.motif.feature.motif.impl.component.MotifScreenTitle
import com.ssafy.motif.core.designsystem.R as DesignSystemR

@Composable
internal fun AssetListScreen(
    contentPadding: PaddingValues,
    onProductClick: (
        artistId: Long,
        assetId: Long,
    ) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AssetListViewModel = hiltViewModel(),
) {
    val uiState by
    viewModel.uiState
        .collectAsStateWithLifecycle()

    LifecycleResumeEffect(
        viewModel,
    ) {
        viewModel.refresh()

        onPauseOrDispose {
            // 별도 정리 작업 없음.
        }
    }

    AssetListScreen(
        uiState = uiState,
        contentPadding = contentPadding,
        onArtistClick =
            viewModel::selectArtist,
        onAssetClick =
            onProductClick,
        modifier = modifier,
    )
}

@Composable
private fun AssetListScreen(
    uiState: AssetListUiState,
    contentPadding: PaddingValues,
    onArtistClick: (Long?) -> Unit,
    onAssetClick: (
        artistId: Long,
        assetId: Long,
    ) -> Unit,
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
            if (
                uiState.assets.isEmpty() &&
                uiState.artists.isEmpty()
            ) {
                AssetListEmptyContent(
                    contentPadding = contentPadding,
                    modifier = modifier,
                )
            } else {
                AssetListContent(
                    uiState = uiState,
                    contentPadding = contentPadding,
                    onArtistClick = onArtistClick,
                    onAssetClick = onAssetClick,
                    modifier = modifier,
                )
            }
        }

        is AssetListUiState.Error -> {
            Box(
                modifier = modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun AssetListEmptyContent(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize(),
    ) {
        MotifScreenTitle(
            text = "MOTIF",
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(
                    bottom = contentPadding.calculateBottomPadding(),
                ),
        ) {
            MotifEmptyContent(
                title = stringResource(
                    R.string.feature_motif_impl_assets_empty_title,
                ),
                description = stringResource(
                    R.string.feature_motif_impl_assets_empty_description,
                ),
            )
        }
    }
}

@Composable
private fun AssetListContent(
    uiState: AssetListUiState.Success,
    contentPadding: PaddingValues,
    onArtistClick: (Long?) -> Unit,
    onAssetClick: (
        artistId: Long,
        assetId: Long,
    ) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(
        uiState.selectedArtistId,
    ) {
        listState.scrollToItem(0)
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
    ) {
        item(
            key = "header",
        ) {
            MotifScreenTitle(
                text = "MOTIF",
            )
        }

        stickyHeader(
            key = "filter",
        ) {
            ArtistFilterRow(
                artists = uiState.artists,
                selectedArtistId = uiState.selectedArtistId,
                onArtistClick = onArtistClick,
                modifier = Modifier.background(
                    MotifTheme.colors.background,
                ),
            )
        }

        if (uiState.assets.isEmpty()) {
            item(
                key = "filtered-empty",
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp),
                ) {
                    MotifEmptyContent(
                        title = stringResource(
                            R.string.feature_motif_impl_assets_filter_empty,
                        ),
                        description = stringResource(
                            R.string.feature_motif_impl_assets_filter_empty_description,
                        ),
                    )
                }
            }
        }

        items(
            items = uiState.assets,
            key = { asset ->
                asset.id
            },
        ) { asset ->
            AssetCard(
                asset = asset,
                onClick = { assetId ->
                    onAssetClick(
                        asset.artist.id,
                        assetId,
                    )
                },
            )
        }
    }
}

@Composable
private fun ArtistFilterRow(
    artists: List<Artist>,
    selectedArtistId: Long?,
    onArtistClick: (Long?) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            horizontal = 16.dp,
            vertical = 12.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(
            16.dp,
        ),
        verticalAlignment = Alignment.Top,
    ) {
        item(
            key = "all",
        ) {
            AllFilterChip(
                selected = selectedArtistId == null,
                onClick = {
                    onArtistClick(null)
                },
            )
        }

        items(
            items = artists,
            key = { artist ->
                artist.id
            },
        ) { artist ->
            ArtistFilterItem(
                artist = artist,
                selected = artist.id == selectedArtistId,
                onClick = {
                    onArtistClick(
                        artist.id,
                    )
                },
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
        targetValue = if (selected) {
            MotifTheme.colors.pink
        } else {
            Color.Transparent
        },
        label = "all chip border",
    )

    Box(
        modifier = modifier
            .size(AllChipSize)
            .clip(CircleShape)
            .background(
                MotifTheme.colors.black,
            )
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
private fun AssetCard(
    asset: Asset,
    onClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                role = Role.Button,
            ) {
                onClick(
                    asset.id,
                )
            },
    ) {
        AsyncImage(
            model = asset.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(
                    16f / 9f,
                ),
        )

        Column(
            modifier = Modifier.padding(
                horizontal = 16.dp,
                vertical = 12.dp,
            ),
        ) {
            Text(
                text = asset.title,
                color = MotifTheme.colors.typo,
                style = MotifTheme.typography.bodyR16,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(
                modifier = Modifier.height(
                    4.dp,
                ),
            )

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

private fun Artist.displayNameWithGroup(): String =
    groupName
        ?.takeIf {
            it.isNotBlank()
        }
        ?.let { groupName ->
            "$name · $groupName"
        }
        ?: name

private val AllChipSize =
    ArtistFilterItemDefaults.Size

private val FilterBorderWidth =
    ArtistFilterItemDefaults.BorderWidth

@Preview(
    name = "Empty",
    showBackground = true,
    heightDp = 900,
)
@Composable
private fun AssetListScreenEmptyPreview() {
    MotifTheme {
        AssetListScreen(
            uiState = AssetListUiState.Success(
                assets = emptyList(),
                artists = emptyList(),
            ),
            contentPadding = PaddingValues(0.dp),
            onArtistClick = {},
            onAssetClick = {_, _ ->},
        )
    }
}

@Preview(
    showBackground = true,
    heightDp = 900,
)
@Composable
private fun AssetListScreenPreview() {
    val artists = previewArtists()

    MotifTheme {
        AssetListScreen(
            uiState = AssetListUiState.Success(
                assets = previewAssets(
                    artists,
                ),
                artists = artists,
            ),
            contentPadding = PaddingValues(
                0.dp,
            ),
            onArtistClick = {},
            onAssetClick = {_, _ ->},
        )
    }
}

@Preview(
    name = "Interactive",
    showBackground = true,
    heightDp = 900,
)
@Composable
private fun AssetListScreenInteractivePreview() {
    val artists = previewArtists()

    val assets = previewAssets(
        artists,
    )

    var uiState by remember {
        mutableStateOf(
            AssetListUiState.Success(
                assets = assets,
                artists = artists,
            ),
        )
    }

    MotifTheme {
        AssetListScreen(
            uiState = uiState,
            contentPadding = PaddingValues(
                0.dp,
            ),
            onArtistClick = { artistId ->
                uiState = uiState.copy(
                    selectedArtistId = artistId,
                    assets = if (
                        artistId == null
                    ) {
                        assets
                    } else {
                        assets.filter { asset ->
                            asset.artist.id ==
                                    artistId
                        }
                    },
                )
            },
            onAssetClick = {_, _ ->},
        )
    }
}

@Preview(
    name = "Loading",
    showBackground = true,
    heightDp = 400,
)
@Composable
private fun AssetListScreenLoadingPreview() {
    MotifTheme {
        AssetListScreen(
            uiState = AssetListUiState.Loading,
            contentPadding = PaddingValues(
                0.dp,
            ),
            onArtistClick = {},
            onAssetClick = {_, _ ->},
        )
    }
}

@Composable
private fun previewImageUrl(
    resId: Int,
): String {
    val context = LocalContext.current

    return "android.resource://${context.packageName}/$resId"
}

@Composable
private fun previewArtists(): List<Artist> {
    val profileUrl = previewImageUrl(
        DesignSystemR.drawable.img_profile,
    )

    return listOf(
        Artist(
            id = 1L,
            name = "YEJI",
            profileImageUrl = profileUrl,
            groupName = "ITZY",
        ),
        Artist(
            id = 2L,
            name = "카리나",
            profileImageUrl = profileUrl,
            groupName = "aespa",
        ),
        Artist(
            id = 3L,
            name = "지수",
            profileImageUrl = profileUrl,
            groupName = "BLACKPINK",
        ),
        Artist(
            id = 4L,
            name = "이채영",
            profileImageUrl = profileUrl,
            groupName = "fromis_9",
        ),
        Artist(
            id = 5L,
            name = "오해원",
            profileImageUrl = profileUrl,
            groupName = "NMIXX",
        ),
    )
}

@Composable
private fun previewAssets(
    artists: List<Artist>,
): List<Asset> {
    val thumbnailUrl = previewImageUrl(
        DesignSystemR.drawable.img_test_motif,
    )

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
            artist = artists[
                index % artists.size
            ],
            availability = AssetAvailability.PURCHASED,
        )
    }
}