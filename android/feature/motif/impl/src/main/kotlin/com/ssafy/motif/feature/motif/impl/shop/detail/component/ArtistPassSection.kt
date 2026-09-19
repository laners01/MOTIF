package com.ssafy.motif.feature.motif.impl.shop.detail.component

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
 * MOTIF ARTIST PASS 영역. 섹션 타이틀, 배너, 안내 헤드라인·설명으로 구성된다.
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

        AsyncImage(
            model = pass.bannerImageUrl,
            contentDescription = stringResource(R.string.feature_motif_impl_product_pass_banner),
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(BannerAspectRatio)
                .clickable(role = Role.Button) { onBannerClick(pass.artistId) },
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
private fun ArtistPassSectionPreview() {
    MotifTheme {
        ArtistPassSection(
            pass = ArtistPass(
                artistId = 1L,
                price = 9_900,
                bannerImageUrl = ShopPreviewFixtures.banners().first().imageUrl,
                headline = "좋아하는 아티스트의 MOTIF를 더 자유롭게 이용해보세요.",
                description = "아티스트 PASS를 구독하면 구독 기간 동안 해당 아티스트의 일반 에셋을 개별 구매 없이 이용할 수 있습니다.",
            ),
            onBannerClick = {},
        )
    }
}
