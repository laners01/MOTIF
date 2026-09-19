package com.ssafy.motif.feature.motif.impl.shop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.motif.core.designsystem.component.LoadingWheel
import com.ssafy.motif.core.designsystem.theme.MotifTheme
import com.ssafy.motif.core.model.motif.AssetAvailability
import com.ssafy.motif.feature.motif.impl.component.LoadingFooter
import com.ssafy.motif.feature.motif.impl.component.MotifScreenTitle
import com.ssafy.motif.feature.motif.impl.shop.component.PassBannerPager
import com.ssafy.motif.feature.motif.impl.shop.component.ShopArtistSection

/**
 * 모티프 서브 네비게이션의 샵 화면.
 *
 * @param contentPadding 서브 네비게이션 바에 가려지지 않도록 콘텐츠에 적용할 여백.
 */
@Composable
internal fun ShopScreen(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    viewModel: ShopViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ShopScreen(
        uiState = uiState,
        contentPadding = contentPadding,
        onBannerClick = viewModel::selectBanner,
        onPassClick = viewModel::selectPass,
        onAssetClick = viewModel::selectAsset,
        onLoadMore = viewModel::loadMore,
        modifier = modifier,
    )
}

@Composable
private fun ShopScreen(
    uiState: ShopUiState,
    contentPadding: PaddingValues,
    onBannerClick: (Long) -> Unit,
    onPassClick: (Long) -> Unit,
    onAssetClick: (Long) -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (uiState) {
        ShopUiState.Loading -> {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .padding(contentPadding),
                contentAlignment = Alignment.Center,
            ) {
                LoadingWheel()
            }
        }

        is ShopUiState.Success -> {
            ShopContent(
                uiState = uiState,
                contentPadding = contentPadding,
                onBannerClick = onBannerClick,
                onPassClick = onPassClick,
                onAssetClick = onAssetClick,
                onLoadMore = onLoadMore,
                modifier = modifier,
            )
        }

        is ShopUiState.Error -> {
            // TODO 에러 상태 UI. Mock 데이터는 실패하지 않으므로 이번 티켓에서는 빈 화면
            Box(modifier = modifier.fillMaxSize())
        }
    }
}

@Composable
private fun ShopContent(
    uiState: ShopUiState.Success,
    contentPadding: PaddingValues,
    onBannerClick: (Long) -> Unit,
    onPassClick: (Long) -> Unit,
    onAssetClick: (Long) -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    val canLoadMore = uiState.hasNext &&
            uiState.appendState is ShopAppendUiState.Idle

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
        item(key = "title") {
            MotifScreenTitle(text = "SHOP")
        }

        if (uiState.banners.isNotEmpty()) {
            item(key = "banners") {
                PassBannerPager(
                    banners = uiState.banners,
                    onBannerClick = onBannerClick,
                )
            }
        }

        items(
            items = uiState.sections,
            key = { it.artist.id },
        ) { section ->
            ShopArtistSection(
                section = section,
                onPassClick = onPassClick,
                onAssetClick = onAssetClick,
                // 배너→첫 섹션, 섹션→섹션 간격. 타이틀 아래 여백은 타이틀 자체 패딩으로만 정한다.
                modifier = Modifier.padding(top = SectionSpacing),
            )
        }

        if (uiState.appendState is ShopAppendUiState.Loading) {
            item(key = "footer") {
                LoadingFooter()
            }
        }
    }
}

private const val LOAD_MORE_THRESHOLD = 1

private val SectionSpacing = 24.dp

@Preview(showBackground = true, heightDp = 1200)
@Composable
private fun ShopScreenPreview() {
    MotifTheme {
        ShopScreen(
            uiState = ShopUiState.Success(
                banners = ShopPreviewFixtures.banners(),
                sections = ShopPreviewFixtures.sections(),
                hasNext = true,
                appendState = ShopAppendUiState.Loading,
            ),
            contentPadding = PaddingValues(0.dp),
            onBannerClick = {},
            onPassClick = {},
            onAssetClick = {},
            onLoadMore = {},
        )
    }
}

/**
 * 실기기 Run Preview용. Pass 구매 버튼을 누르면 P 뱃지로, 카드를 누르면 구매 완료로 즉시 바뀐다.
 * ViewModel의 onPassPurchased / onAssetPurchased와 같은 갱신을 Preview 안에서 흉내 낸다.
 */
@Preview(name = "Interactive", showBackground = true, heightDp = 1200)
@Composable
private fun ShopScreenInteractivePreview() {
    val initial = ShopUiState.Success(
        banners = ShopPreviewFixtures.banners(),
        sections = ShopPreviewFixtures.sections(),
        hasNext = false,
    )

    var uiState by remember { mutableStateOf(initial) }

    MotifTheme {
        ShopScreen(
            uiState = uiState,
            contentPadding = PaddingValues(0.dp),
            onBannerClick = {},
            onPassClick = { artistId ->
                uiState = uiState.copy(
                    sections = uiState.sections.map { section ->
                        if (section.artist.id == artistId) section.copy(hasPass = true) else section
                    },
                )
            },
            onAssetClick = { assetId ->
                uiState = uiState.copy(
                    sections = uiState.sections.map { section ->
                        section.copy(
                            assets = section.assets.map { asset ->
                                if (asset.id == assetId) {
                                    asset.copy(availability = AssetAvailability.PURCHASED)
                                } else {
                                    asset
                                }
                            },
                        )
                    },
                )
            },
            onLoadMore = {},
        )
    }
}

@Preview(name = "Loading", showBackground = true, heightDp = 400)
@Composable
private fun ShopScreenLoadingPreview() {
    MotifTheme {
        ShopScreen(
            uiState = ShopUiState.Loading,
            contentPadding = PaddingValues(0.dp),
            onBannerClick = {},
            onPassClick = {},
            onAssetClick = {},
            onLoadMore = {},
        )
    }
}
