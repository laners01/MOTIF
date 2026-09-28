package com.ssafy.motif.feature.motif.impl.shop.detail.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ssafy.motif.core.designsystem.component.Text
import com.ssafy.motif.core.designsystem.theme.MotifTheme

/**
 * 상품 상세의 아티스트명 + 2줄 소개 문구.
 */
@Composable
internal fun ArtistIntroSection(
    artistName: String,
    introduction: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text = artistName,
            color = MotifTheme.colors.white,
            style = MotifTheme.typography.titleSB18,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = introduction,
            color = MotifTheme.colors.gray400,
            style = MotifTheme.typography.bodyR12,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ArtistIntroSectionPreview() {
    MotifTheme {
        ArtistIntroSection(
            artistName = "Yeji",
            introduction = "선물처럼 찾아온 최애와 함께하는 설레는 일상.\n최애 아티스트와 만드는 나만의 특별한 순간, MOTIF",
            modifier = Modifier.padding(16.dp),
        )
    }
}
