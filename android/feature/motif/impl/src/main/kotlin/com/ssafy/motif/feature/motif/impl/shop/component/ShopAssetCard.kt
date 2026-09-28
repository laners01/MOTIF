package com.ssafy.motif.feature.motif.impl.shop.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ssafy.motif.core.designsystem.component.Surface
import com.ssafy.motif.core.designsystem.component.Text
import com.ssafy.motif.core.designsystem.theme.MotifTheme
import com.ssafy.motif.core.model.motif.Asset
import com.ssafy.motif.core.model.motif.AssetAvailability
import com.ssafy.motif.feature.motif.impl.R
import com.ssafy.motif.feature.motif.impl.shop.ShopPreviewFixtures

/**
 * 샵 섹션의 가로 스크롤 에셋 카드. 구매 완료 에셋은 썸네일 우하단에 뱃지를 표시한다.
 *
 * TODO 프리미엄 에셋 구분 표시. 디자인 확정 후 [Asset.isPremium] 기준으로 추가
 */
@Composable
internal fun ShopAssetCard(
    asset: Asset,
    onClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(CardWidth)
            .clickable(role = Role.Button) { onClick(asset.id) },
    ) {
        Box {
            AsyncImage(
                model = asset.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(ThumbnailCornerRadius)),
            )

            if (asset.availability == AssetAvailability.PURCHASED) {
                PurchasedBadge(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = asset.title,
            color = MotifTheme.colors.typo,
            style = MotifTheme.typography.bodyR14,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PurchasedBadge(
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(percent = 50),
        color = Color.Black.copy(alpha = 0.6f),
        contentColor = MotifTheme.colors.white,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = stringResource(R.string.feature_motif_impl_purchased),
            style = MotifTheme.typography.captionSB10,
        )
    }
}

private val CardWidth = 235.dp
private val ThumbnailCornerRadius = 12.dp

@Preview(showBackground = true)
@Composable
private fun ShopAssetCardPreview() {
    val artist = ShopPreviewFixtures.artists().first()

    MotifTheme {
        ShopAssetCard(
            asset = ShopPreviewFixtures.assets(artist).first(),
            onClick = {},
        )
    }
}

@Preview(name = "Purchased", showBackground = true)
@Composable
private fun ShopAssetCardPurchasedPreview() {
    val artist = ShopPreviewFixtures.artists().first()

    MotifTheme {
        ShopAssetCard(
            asset = ShopPreviewFixtures.assets(artist, purchasedIndices = setOf(0)).first(),
            onClick = {},
        )
    }
}
