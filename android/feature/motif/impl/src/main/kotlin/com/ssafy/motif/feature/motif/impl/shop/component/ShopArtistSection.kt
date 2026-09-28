package com.ssafy.motif.feature.motif.impl.shop.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ssafy.motif.core.designsystem.theme.MotifTheme
import com.ssafy.motif.core.model.shop.ShopArtistSection
import com.ssafy.motif.feature.motif.impl.shop.ShopPreviewFixtures

/**
 * 아티스트 한 명의 상품 섹션. 헤더 아래에 에셋 카드를 가로 스크롤로 나열한다.
 *
 * @param onAssetClick 카드 선택. 상세 화면 진입에 아티스트 id가 함께 필요해서 (artistId, assetId)로 올린다.
 */
@Composable
internal fun ShopArtistSection(
    section: ShopArtistSection,
    onPassClick: (Long) -> Unit,
    onAssetClick: (artistId: Long, assetId: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        ShopArtistSectionHeader(
            artist = section.artist,
            hasPass = section.hasPass,
            onPassClick = { onPassClick(section.artist.id) },
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        Spacer(modifier = Modifier.height(12.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(
                items = section.assets,
                key = { it.id },
            ) { asset ->
                ShopAssetCard(
                    asset = asset,
                    onClick = { assetId -> onAssetClick(section.artist.id, assetId) },
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ShopArtistSectionPreview() {
    MotifTheme {
        ShopArtistSection(
            section = ShopPreviewFixtures.sections()[1],
            onPassClick = {},
            onAssetClick = { _, _ -> },
        )
    }
}
