package com.ssafy.motif.feature.motif.impl.shop.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.motif.core.designsystem.component.LoadingWheel
import com.ssafy.motif.core.designsystem.theme.MotifTheme
import com.ssafy.motif.core.model.motif.AssetAvailability
import com.ssafy.motif.core.model.shop.ShopArtistDetail
import com.ssafy.motif.feature.motif.impl.component.MotifVideoPlayer
import com.ssafy.motif.feature.motif.impl.shop.ShopPreviewFixtures
import com.ssafy.motif.feature.motif.impl.shop.component.ShopAssetCard
import com.ssafy.motif.feature.motif.impl.shop.detail.component.ArtistIntroSection
import com.ssafy.motif.feature.motif.impl.shop.detail.component.ArtistPassSection
import com.ssafy.motif.feature.motif.impl.shop.detail.component.ProductGuideSection
import com.ssafy.motif.feature.motif.impl.shop.detail.component.PurchaseButton
import com.ssafy.motif.feature.motif.impl.shop.detail.component.SelectedProductCard

/**
 * 아티스트별 모티프 상품 상세.
 *
 * @param artistId 상세를 볼 아티스트.
 * @param assetId 진입 시 선택할 에셋.
 * @param onPassClick PASS 배너 선택 → PASS 결제 화면 이동.
 */
@Composable
internal fun ProductDetailScreen(
    artistId: Long,
    assetId: Long,
    onPassClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProductDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(artistId, assetId) {
        viewModel.load(artistId = artistId, assetId = assetId)
    }

    ProductDetailScreen(
        uiState = uiState,
        onAssetClick = viewModel::selectAsset,
        onPassClick = onPassClick,
        onPurchaseClick = viewModel::purchaseSelectedAsset,
        modifier = modifier,
    )
}

@Composable
private fun ProductDetailScreen(
    uiState: ProductDetailUiState,
    onAssetClick: (Long) -> Unit,
    onPassClick: (Long) -> Unit,
    onPurchaseClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (uiState) {
        ProductDetailUiState.Loading -> {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                LoadingWheel()
            }
        }

        is ProductDetailUiState.Success -> {
            ProductDetailContent(
                uiState = uiState,
                onAssetClick = onAssetClick,
                onPassClick = onPassClick,
                onPurchaseClick = onPurchaseClick,
                modifier = modifier,
            )
        }

        is ProductDetailUiState.Error -> {
            // TODO 에러 상태 UI. Mock 데이터는 실패하지 않으므로 이번 티켓에서는 빈 화면
            Box(modifier = modifier.fillMaxSize())
        }
    }
}

@Composable
private fun ProductDetailContent(
    uiState: ProductDetailUiState.Success,
    onAssetClick: (Long) -> Unit,
    onPassClick: (Long) -> Unit,
    onPurchaseClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val detail = uiState.detail
    val selectedAsset = uiState.selectedAsset

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MotifTheme.colors.background),
    ) {
        // 내용 길이가 정해져 있고(페이징 없음) 플레이어가 스크롤 밖으로 나가도 재생을 유지해야 하므로
        // LazyColumn 대신 verticalScroll을 쓴다.
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            MotifVideoPlayer(
                videoId = selectedAsset.id.toString(),
                videoUrl = selectedAsset.previewVideoUrl,
                thumbnailUrl = selectedAsset.thumbnailUrl,
            )

            ArtistIntroSection(
                artistName = detail.artist.name,
                introduction = detail.introduction,
                modifier = Modifier.padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = 20.dp,
                ),
            )

            SelectedProductCard(
                asset = selectedAsset,
                modifier = Modifier.padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = 16.dp,
                ),
            )

            Spacer(modifier = Modifier.height(SectionSpacing))

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(
                    items = detail.assets,
                    key = { it.id },
                ) { asset ->
                    ShopAssetCard(
                        asset = asset,
                        onClick = onAssetClick,
                    )
                }
            }

            Spacer(modifier = Modifier.height(SectionSpacing))

            ArtistPassSection(
                pass = detail.pass,
                onBannerClick = onPassClick,
            )

            Spacer(modifier = Modifier.height(SectionSpacing))

            ProductGuideSection(
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            Spacer(modifier = Modifier.height(SectionSpacing))
        }

        // 스크롤과 무관하게 하단 고정
        PurchaseButton(
            isPurchased = uiState.isSelectedAssetPurchased,
            onClick = onPurchaseClick,
            modifier = Modifier
                .navigationBarsPadding()
                .padding(
                    horizontal = 16.dp,
                    vertical = 12.dp,
                ),
        )
    }
}

private val SectionSpacing = 32.dp

@Preview(showBackground = true, heightDp = 1400)
@Composable
private fun ProductDetailScreenPreview() {
    MotifTheme {
        ProductDetailScreen(
            uiState = previewSuccessState(),
            onAssetClick = {},
            onPassClick = {},
            onPurchaseClick = {},
        )
    }
}

@Preview(name = "Purchased selected", showBackground = true, heightDp = 1400)
@Composable
private fun ProductDetailScreenPurchasedPreview() {
    val state = previewSuccessState()

    MotifTheme {
        ProductDetailScreen(
            uiState = state.copy(
                selectedAsset = state.detail.assets.first { it.availability == AssetAvailability.PURCHASED },
            ),
            onAssetClick = {},
            onPassClick = {},
            onPurchaseClick = {},
        )
    }
}

@Preview(name = "Loading", showBackground = true, heightDp = 400)
@Composable
private fun ProductDetailScreenLoadingPreview() {
    MotifTheme {
        ProductDetailScreen(
            uiState = ProductDetailUiState.Loading,
            onAssetClick = {},
            onPassClick = {},
            onPurchaseClick = {},
        )
    }
}

@Composable
private fun previewSuccessState(): ProductDetailUiState.Success {
    val artist = ShopPreviewFixtures.artists().first()
    val assets = ShopPreviewFixtures.assets(artist, purchasedIndices = setOf(1)).map {
        it.copy(price = 3_900)
    }

    return ProductDetailUiState.Success(
        detail = ShopArtistDetail(
            artist = artist,
            introduction = "선물처럼 찾아온 최애와 함께하는 설레는 일상.\n최애 아티스트와 만드는 나만의 특별한 순간, MOTIF",
            assets = assets,
            pass = ShopPreviewFixtures.pass(artist.id),
            hasPass = false,
        ),
        selectedAsset = assets.first(),
    )
}
