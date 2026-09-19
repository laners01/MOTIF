package com.ssafy.motif.feature.motif.impl.archive.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.motif.core.designsystem.component.Text
import com.ssafy.motif.core.designsystem.theme.MotifTheme
import com.ssafy.motif.core.model.home.Artist
import com.ssafy.motif.feature.motif.impl.archive.ArchiveMockData
import com.ssafy.motif.feature.motif.impl.component.ArtistFilterItem

/**
 * 함께 제작한 아티스트로 목록을 거르는 WITH 필터.
 * 흰 테두리 박스 위에 WITH 라벨이 테두리를 끊으며 겹쳐 있고, 안에 아티스트 항목이 가로로 나열된다.
 *
 * @param selectedArtistId 선택된 아티스트 id. null이면 전체.
 */
@Composable
internal fun WithArtistFilter(
    artists: List<Artist>,
    selectedArtistId: Long?,
    onArtistClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            // 라벨이 박스 위로 절반 튀어나오는 만큼 위 여백을 둔다.
            .padding(top = LabelOverflow),
    ) {
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = BorderWidth,
                    color = MotifTheme.colors.white,
                    shape = RoundedCornerShape(BoxCornerRadius),
                ),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                // 라벨이 겹치는 위쪽은 더 띄운다.
                top = LabelOverflow + 8.dp,
                bottom = 12.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(artists, key = { it.id }) { artist ->
                ArtistFilterItem(
                    artist = artist,
                    selected = artist.id == selectedArtistId,
                    onClick = { onArtistClick(artist.id) },
                    size = ArtistItemSize,
                )
            }
        }

        Text(
            text = "WITH",
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = -LabelOverflow)
                // 배경색으로 뒤의 테두리를 가려 라벨이 테두리를 끊는 것처럼 보이게 한다.
                .background(MotifTheme.colors.background)
                .padding(horizontal = 8.dp),
            color = MotifTheme.colors.white,
            style = MotifTheme.typography.rankR36.copy(
                fontSize = 28.sp,
                lineHeight = 28.sp,
            ),
        )
    }
}

private val BorderWidth = 1.5.dp
private val BoxCornerRadius = 20.dp
private val ArtistItemSize = 64.dp

// 라벨 높이(약 28dp)의 절반. 라벨이 테두리 선을 정확히 반으로 가르도록 한다.
private val LabelOverflow = 14.dp

@Preview(showBackground = true)
@Composable
private fun WithArtistFilterPreview() {
    MotifTheme {
        WithArtistFilter(
            artists = listOf(
                ArchiveMockData.karina,
                ArchiveMockData.jisoo,
                ArchiveMockData.chaeyoung,
                ArchiveMockData.haewon,
                ArchiveMockData.yeji,
            ),
            selectedArtistId = ArchiveMockData.jisoo.id,
            onArtistClick = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}
