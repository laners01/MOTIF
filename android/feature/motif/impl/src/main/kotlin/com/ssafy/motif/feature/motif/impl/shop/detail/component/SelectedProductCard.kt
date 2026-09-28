package com.ssafy.motif.feature.motif.impl.shop.detail.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ssafy.motif.core.designsystem.component.Surface
import com.ssafy.motif.core.designsystem.component.Text
import com.ssafy.motif.core.designsystem.theme.MotifTheme
import com.ssafy.motif.core.model.motif.Asset
import com.ssafy.motif.core.ui.formatter.formatPrice
import com.ssafy.motif.feature.motif.impl.shop.ShopPreviewFixtures

/**
 * 현재 선택된 에셋의 소개 문구와 가격을 보여주는 카드.
 */
@Composable
internal fun SelectedProductCard(
    asset: Asset,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CardCornerRadius),
        color = MotifTheme.colors.black,
        contentColor = MotifTheme.colors.white,
        contentPadding = PaddingValues(16.dp),
    ) {
        Column {
            Text(
                text = asset.title,
                style = MotifTheme.typography.bodySB16,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = asset.price.formatPrice(),
                style = MotifTheme.typography.titleSB18,
                modifier = Modifier.align(Alignment.End),
            )
        }
    }
}

private val CardCornerRadius = 12.dp

@Preview(showBackground = true)
@Composable
private fun SelectedProductCardPreview() {
    val artist = ShopPreviewFixtures.artists().first()

    MotifTheme {
        SelectedProductCard(
            asset = ShopPreviewFixtures.assets(artist).first().copy(price = 3_900),
            modifier = Modifier.padding(16.dp),
        )
    }
}
