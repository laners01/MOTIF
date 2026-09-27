package com.ssafy.motif.feature.motif.impl.shop.pass

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.motif.core.designsystem.component.LoadingWheel
import com.ssafy.motif.core.designsystem.component.Text
import com.ssafy.motif.core.designsystem.theme.MotifTheme
import com.ssafy.motif.core.model.shop.ShopArtistDetail
import com.ssafy.motif.feature.motif.impl.R
import com.ssafy.motif.feature.motif.impl.component.MotifScreenTitle
import com.ssafy.motif.feature.motif.impl.payment.MotifPaymentEvent
import com.ssafy.motif.feature.motif.impl.payment.rememberMotifPaymentLauncher
import com.ssafy.motif.feature.motif.impl.shop.ShopPreviewFixtures
import com.ssafy.motif.feature.motif.impl.shop.component.PassIntro
import com.ssafy.motif.feature.motif.impl.shop.component.ShopAssetCard
import com.ssafy.motif.feature.motif.impl.shop.pass.component.PassBenefitSection
import com.ssafy.motif.feature.motif.impl.shop.pass.component.PassGuideSection
import com.ssafy.motif.feature.motif.impl.shop.pass.component.PassPlanCard

@Composable
internal fun PassPurchaseScreen(
    artistId: Long,
    onAssetClick:
        (
        artistId: Long,
        assetId: Long,
    ) -> Unit,
    modifier: Modifier = Modifier,
    viewModel:
    PassPurchaseViewModel =
        hiltViewModel(),
) {
    val uiState by
    viewModel.uiState
        .collectAsStateWithLifecycle()

    val context =
        LocalContext.current

    val fallbackError =
        stringResource(
            R.string
                .feature_motif_impl_payment_error,
        )

    val launchPayment =
        rememberMotifPaymentLauncher(
            onSuccess = {
                    paymentKey,
                    orderId,
                    amount,
                ->
                viewModel.confirmPayment(
                    paymentKey = paymentKey,
                    orderId = orderId,
                    amount = amount,
                )
            },
            onCanceled = {
                viewModel
                    .cancelPendingOrder()
            },
            onFailure = { message ->
                viewModel
                    .cancelPendingOrder()

                Toast
                    .makeText(
                        context,
                        message.ifBlank {
                            fallbackError
                        },
                        Toast.LENGTH_SHORT,
                    )
                    .show()
            },
        )

    LaunchedEffect(
        artistId,
    ) {
        viewModel.load(
            artistId,
        )
    }

    LaunchedEffect(
        viewModel,
    ) {
        viewModel
            .paymentEvents
            .collect { event ->
                when (event) {
                    is MotifPaymentEvent.Launch ->
                        launchPayment(
                            event.order,
                        )

                    is MotifPaymentEvent.Error ->
                        Toast
                            .makeText(
                                context,
                                event.throwable
                                    .message
                                    ?: fallbackError,
                                Toast.LENGTH_SHORT,
                            )
                            .show()
                }
            }
    }

    PassPurchaseScreen(
        uiState = uiState,
        onAssetClick = onAssetClick,
        onPurchaseClick =
            viewModel::purchasePlan,
        modifier = modifier,
    )
}

@Composable
private fun PassPurchaseScreen(
    uiState: PassPurchaseUiState,
    onAssetClick:
        (
        artistId: Long,
        assetId: Long,
    ) -> Unit,
    onPurchaseClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (uiState) {
        PassPurchaseUiState.Loading -> {
            Box(
                modifier =
                    modifier.fillMaxSize(),
                contentAlignment =
                    Alignment.Center,
            ) {
                LoadingWheel()
            }
        }

        is PassPurchaseUiState.Success -> {
            PassPurchaseContent(
                detail =
                    uiState.detail,
                onAssetClick =
                    onAssetClick,
                onPurchaseClick =
                    onPurchaseClick,
                modifier =
                    modifier,
            )
        }

        is PassPurchaseUiState.Error -> {
            Box(
                modifier =
                    modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun PassPurchaseContent(
    detail: ShopArtistDetail,
    onAssetClick:
        (
        artistId: Long,
        assetId: Long,
    ) -> Unit,
    onPurchaseClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(
                    MotifTheme.colors.background,
                )
                .verticalScroll(
                    rememberScrollState(),
                )
                .navigationBarsPadding(),
    ) {
        MotifScreenTitle(
            text =
                stringResource(
                    R.string
                        .feature_motif_impl_product_pass_title,
                ),
        )

        PassIntro(
            pass = detail.pass,
        )

        Spacer(
            modifier =
                Modifier.height(
                    SectionSpacing,
                ),
        )

        LazyRow(
            contentPadding =
                PaddingValues(
                    horizontal = 16.dp,
                ),
            horizontalArrangement =
                Arrangement.spacedBy(
                    12.dp,
                ),
        ) {
            items(
                items =
                    detail.assets,
                key = {
                    it.id
                },
            ) { asset ->
                ShopAssetCard(
                    asset =
                        asset,
                    onClick = {
                            assetId ->
                        onAssetClick(
                            detail.artist.id,
                            assetId,
                        )
                    },
                )
            }
        }

        Spacer(
            modifier =
                Modifier.height(
                    SectionSpacing,
                ),
        )

        Text(
            text =
                stringResource(
                    R.string
                        .feature_motif_impl_pass_purchase_with,
                    detail.artist
                        .name
                        .uppercase(),
                ),
            color =
                MotifTheme.colors.white,
            style =
                MotifTheme.typography.titleSB18,
            modifier =
                Modifier.padding(
                    horizontal = 16.dp,
                ),
        )

        Spacer(
            modifier =
                Modifier.height(
                    12.dp,
                ),
        )

        Column(
            modifier =
                Modifier.padding(
                    horizontal = 16.dp,
                ),
            verticalArrangement =
                Arrangement.spacedBy(
                    12.dp,
                ),
        ) {
            detail.pass
                .plans
                .forEach { plan ->
                    PassPlanCard(
                        plan = plan,
                        onPurchaseClick =
                            onPurchaseClick,
                    )
                }
        }

        Spacer(
            modifier =
                Modifier.height(
                    SectionSpacing,
                ),
        )

        PassBenefitSection(
            modifier =
                Modifier.padding(
                    horizontal = 16.dp,
                ),
        )

        Spacer(
            modifier =
                Modifier.height(
                    SectionSpacing,
                ),
        )

        PassGuideSection(
            modifier =
                Modifier.padding(
                    horizontal = 16.dp,
                ),
        )

        Spacer(
            modifier =
                Modifier.height(
                    SectionSpacing,
                ),
        )
    }
}

private val SectionSpacing =
    32.dp

@Preview(
    showBackground = true,
    heightDp = 1300,
)
@Composable
private fun PassPurchaseScreenPreview() {
    val artist =
        ShopPreviewFixtures
            .artists()
            .first()

    MotifTheme {
        PassPurchaseScreen(
            uiState =
                PassPurchaseUiState
                    .Success(
                        detail =
                            ShopArtistDetail(
                                artist =
                                    artist,
                                introduction =
                                    "",
                                assets =
                                    ShopPreviewFixtures
                                        .assets(
                                            artist,
                                        ),
                                pass =
                                    ShopPreviewFixtures
                                        .pass(
                                            artist.id,
                                        ),
                                hasPass =
                                    false,
                            ),
                    ),
            onAssetClick = {
                    _,
                    _,
                ->
            },
            onPurchaseClick = {},
        )
    }
}

@Preview(
    name = "Loading",
    showBackground = true,
    heightDp = 400,
)
@Composable
private fun PassPurchaseScreenLoadingPreview() {
    MotifTheme {
        PassPurchaseScreen(
            uiState =
                PassPurchaseUiState
                    .Loading,
            onAssetClick = {
                    _,
                    _,
                ->
            },
            onPurchaseClick = {},
        )
    }
}