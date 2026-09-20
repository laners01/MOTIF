package com.ssafy.motif.feature.motif.impl.shop.detail.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ssafy.motif.core.designsystem.component.Text
import com.ssafy.motif.core.designsystem.theme.MotifTheme
import com.ssafy.motif.core.model.shop.ArtistPass
import com.ssafy.motif.feature.motif.impl.R
import com.ssafy.motif.feature.motif.impl.shop.ShopPreviewFixtures
import com.ssafy.motif.feature.motif.impl.shop.component.PassIntro

/**
 * 상품 상세의 MOTIF ARTIST PASS 영역. 섹션 타이틀 + 배너·안내([PassIntro]).
 * 배너를 누르면 PASS 결제 화면으로 이동한다.
 */
@Composable
internal fun ArtistPassSection(
    pass: ArtistPass,
    onBannerClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text = stringResource(R.string.feature_motif_impl_product_pass_title),
            color = MotifTheme.colors.white,
            style = MotifTheme.typography.titleB22,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        Spacer(modifier = Modifier.height(12.dp))

        PassIntro(
            pass = pass,
            onBannerClick = onBannerClick,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ArtistPassSectionPreview() {
    MotifTheme {
        ArtistPassSection(
            pass = ShopPreviewFixtures.pass(),
            onBannerClick = {},
        )
    }
}
