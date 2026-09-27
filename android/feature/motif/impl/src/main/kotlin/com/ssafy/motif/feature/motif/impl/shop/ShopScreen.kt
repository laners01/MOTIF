package com.ssafy.motif.feature.motif.impl.shop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.motif.core.designsystem.component.LoadingWheel
import com.ssafy.motif.core.designsystem.theme.MotifTheme
import com.ssafy.motif.core.model.motif.AssetAvailability
import com.ssafy.motif.feature.motif.impl.component.MotifScreenTitle
import com.ssafy.motif.feature.motif.impl.shop.component.PassBannerPager
import com.ssafy.motif.feature.motif.impl.shop.component.ShopArtistSection

@Composable
internal fun ShopScreen(
    contentPadding: PaddingValues,
    onProductClick:
        (
        artistId: Long,
        assetId: Long,
    ) -> Unit,
    onPassClick:
        (
        artistId: Long,
    ) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ShopViewModel = hiltViewModel(),
) {
    val uiState by
    viewModel.uiState
        .collectAsStateWithLifecycle()

    ShopScreen(
        uiState = uiState,
        contentPadding =
            contentPadding,

        // 공용 배너 API에는 클릭 액션이 없다.
        onBannerClick = {},

        onPassClick =
            onPassClick,
        onAssetClick =
            onProductClick,
        modifier = modifier,
    )
}

@Composable
private fun ShopScreen(
    uiState: ShopUiState,
    contentPadding: PaddingValues,
    onBannerClick: (Long) -> Unit,
    onPassClick: (Long) -> Unit,
    onAssetClick:
        (
        artistId: Long,
        assetId: Long,
    ) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (uiState) {
        ShopUiState.Loading -> {
            Box(
                modifier =
                    modifier
                        .fillMaxSize()
                        .padding(
                            contentPadding,
                        ),
                contentAlignment =
                    Alignment.Center,
            ) {
                LoadingWheel()
            }
        }

        is ShopUiState.Success -> {
            ShopContent(
                uiState = uiState,
                contentPadding =
                    contentPadding,
                onBannerClick =
                    onBannerClick,
                onPassClick =
                    onPassClick,
                onAssetClick =
                    onAssetClick,
                modifier = modifier,
            )
        }

        is ShopUiState.Error -> {
            Box(
                modifier =
                    modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun ShopContent(
    uiState: ShopUiState.Success,
    contentPadding: PaddingValues,
    onBannerClick: (Long) -> Unit,
    onPassClick: (Long) -> Unit,
    onAssetClick:
        (
        artistId: Long,
        assetId: Long,
    ) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier =
            modifier.fillMaxSize(),
        contentPadding =
            contentPadding,
    ) {
        item(
            key = "title",
        ) {
            MotifScreenTitle(
                text = "SHOP",
            )
        }

        if (
            uiState.banners
                .isNotEmpty()
        ) {
            item(
                key = "banners",
            ) {
                PassBannerPager(
                    banners =
                        uiState.banners,
                    onBannerClick =
                        onBannerClick,
                )
            }
        }

        items(
            items =
                uiState.sections,
            key = { section ->
                section.artist.id
            },
        ) { section ->
            ShopArtistSection(
                section = section,
                onPassClick =
                    onPassClick,
                onAssetClick =
                    onAssetClick,
                modifier =
                    Modifier.padding(
                        top =
                            SectionSpacing,
                    ),
            )
        }
    }
}

private val SectionSpacing =
    24.dp

@Preview(
    showBackground = true,
    heightDp = 1200,
)
@Composable
private fun ShopScreenPreview() {
    MotifTheme {
        ShopScreen(
            uiState =
                ShopUiState.Success(
                    banners =
                        ShopPreviewFixtures
                            .banners(),
                    sections =
                        ShopPreviewFixtures
                            .sections(),
                ),
            contentPadding =
                PaddingValues(
                    0.dp,
                ),
            onBannerClick = {},
            onPassClick = {},
            onAssetClick = {
                    _,
                    _,
                ->
            },
        )
    }
}

@Preview(
    name = "Interactive",
    showBackground = true,
    heightDp = 1200,
)
@Composable
private fun ShopScreenInteractivePreview() {
    val initial =
        ShopUiState.Success(
            banners =
                ShopPreviewFixtures
                    .banners(),
            sections =
                ShopPreviewFixtures
                    .sections(),
        )

    var uiState by
    remember {
        mutableStateOf(
            initial,
        )
    }

    MotifTheme {
        ShopScreen(
            uiState = uiState,
            contentPadding =
                PaddingValues(
                    0.dp,
                ),
            onBannerClick = {},
            onPassClick = { artistId ->
                uiState =
                    uiState.copy(
                        sections =
                            uiState.sections
                                .map {
                                        section,
                                    ->
                                    if (
                                        section
                                            .artist
                                            .id ==
                                        artistId
                                    ) {
                                        section.copy(
                                            hasPass =
                                                true,
                                        )
                                    } else {
                                        section
                                    }
                                },
                    )
            },
            onAssetClick = {
                    _,
                    assetId,
                ->
                uiState =
                    uiState.copy(
                        sections =
                            uiState.sections
                                .map {
                                        section,
                                    ->
                                    section.copy(
                                        assets =
                                            section.assets
                                                .map {
                                                        asset,
                                                    ->
                                                    if (
                                                        asset.id ==
                                                        assetId
                                                    ) {
                                                        asset.copy(
                                                            availability =
                                                                AssetAvailability
                                                                    .PURCHASED,
                                                        )
                                                    } else {
                                                        asset
                                                    }
                                                },
                                    )
                                },
                    )
            },
        )
    }
}

@Preview(
    name = "Loading",
    showBackground = true,
    heightDp = 400,
)
@Composable
private fun ShopScreenLoadingPreview() {
    MotifTheme {
        ShopScreen(
            uiState =
                ShopUiState.Loading,
            contentPadding =
                PaddingValues(
                    0.dp,
                ),
            onBannerClick = {},
            onPassClick = {},
            onAssetClick = {
                    _,
                    _,
                ->
            },
        )
    }
}