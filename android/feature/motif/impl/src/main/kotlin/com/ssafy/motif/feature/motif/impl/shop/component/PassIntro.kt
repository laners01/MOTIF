package com.ssafy.motif.feature.motif.impl.shop.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ssafy.motif.core.designsystem.component.Text
import com.ssafy.motif.core.designsystem.theme.MotifTheme
import com.ssafy.motif.core.model.shop.ArtistPass
import com.ssafy.motif.feature.motif.impl.R
import com.ssafy.motif.feature.motif.impl.shop.ShopPreviewFixtures

/**
 * PASS 배너 + 안내 헤드라인·설명. 상품 상세와 PASS 결제 화면이 공용으로 쓴다.
 *
 * @param onBannerClick null이면 배너를 눌러도 반응하지 않는다 (PASS 결제 화면 안에서).
 */
@Composable
internal fun PassIntro(
    pass: ArtistPass,
    modifier: Modifier = Modifier,
    onBannerClick: ((Long) -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        AsyncImage(
            model = pass.bannerImageUrl,
            contentDescription = stringResource(R.string.feature_motif_impl_product_pass_banner),
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(BannerAspectRatio)
                .then(
                    if (onBannerClick != null) {
                        Modifier.clickable(role = Role.Button) { onBannerClick(pass.artistId) }
                    } else {
                        Modifier
                    },
                ),
        )

        Spacer(modifier = Modifier.height(16.dp))

        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            Text(
                text = pass.headline,
                color = MotifTheme.colors.white,
                style = MotifTheme.typography.bodySB16,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = pass.description,
                color = MotifTheme.colors.gray400,
                style = MotifTheme.typography.bodyR12,
            )
        }
    }
}

// 샵 배너와 동일한 비율
private const val BannerAspectRatio = 8f / 3f

@Preview(showBackground = true)
@Composable
private fun PassIntroPreview() {
    MotifTheme {
        PassIntro(
            pass = ShopPreviewFixtures.pass(),
        )
    }
}
