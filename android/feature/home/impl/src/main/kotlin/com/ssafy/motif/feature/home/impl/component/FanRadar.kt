package com.ssafy.motif.feature.home.impl.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ssafy.motif.core.designsystem.component.ClickableSurface
import com.ssafy.motif.core.designsystem.component.Text
import com.ssafy.motif.core.designsystem.theme.MotifTheme
import com.ssafy.motif.core.model.artist.Artist
import com.ssafy.motif.core.model.artist.RankedArtist

@Composable
internal fun FanRadar(
    artists: List<RankedArtist>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
    ) {
        ClickableSurface(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = MotifTheme.colors.white,
                    shape = RoundedCornerShape(12.dp),
                ),
            shape = RoundedCornerShape(20.dp),
            color = Color.Transparent,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = 20.dp,
                        end = 20.dp,
                        top = 28.dp,
                        bottom = 14.dp,
                    ),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                artists
                    .take(FAN_RADAR_ITEM_COUNT)
                    .forEach { artist ->
                        FanRadarArtistItem(
                            rankedArtist = artist,
                            modifier = Modifier.weight(1f),
                        )
                    }
            }
        }

        Text(
            text = "Who's On",
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(
                    x = 20.dp,
                    y = (-8).dp,
                )
                .background(MotifTheme.colors.background)
                .padding(horizontal = 6.dp),
            color = MotifTheme.colors.white,
            style = MotifTheme.typography.bodySB16,
        )
    }
}

@Composable
private fun FanRadarArtistItem(
    rankedArtist: RankedArtist,
    modifier: Modifier = Modifier,
) {
    val strokeWidth = with(LocalDensity.current) {
        1.dp.toPx()
    }

    Box(
        modifier = modifier.size(80.dp)
    ) {
        AsyncImage(
            model = rankedArtist.artist.profileImageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .size(60.dp)
                .clip(CircleShape),
        )

        Text(
            text = rankedArtist.rank.toString(),
            color = MotifTheme.colors.typo,
            style = MotifTheme.typography.rankR36.copy(
                drawStyle = Stroke(
                    width = strokeWidth,
                ),
            ),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(y = 4.dp),
        )

        Text(
            text = rankedArtist.artist.name,
            color = MotifTheme.colors.typo,
            style = MotifTheme.typography.captionSB12,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
    }
}

private const val FAN_RADAR_ITEM_COUNT = 4

@Preview(showBackground = true)
@Composable
private fun FanRadarPreview() {
    MotifTheme {
        FanRadar(
            artists = previewRankedArtists,
            onClick = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}

private val previewRankedArtists = listOf(
    RankedArtist(
        artist = Artist(
            id = 1L,
            name = "카리나",
            profileImageUrl = "https://picsum.photos/seed/karina/200",
            groupName = "aespa",
        ),
        rank = 1,
        rankDelta = 0,
        isSubscribed = true,
    ),
    RankedArtist(
        artist = Artist(
            id = 2L,
            name = "지수",
            profileImageUrl = "https://picsum.photos/seed/jisoo/200",
            groupName = "BLACKPINK",
        ),
        rank = 2,
        rankDelta = 1,
        isSubscribed = false,
    ),
    RankedArtist(
        artist = Artist(
            id = 3L,
            name = "이채영",
            profileImageUrl = "https://picsum.photos/seed/chaeyoung/200",
            groupName = "프로미스나인",
        ),
        rank = 3,
        rankDelta = -1,
        isSubscribed = true,
    ),
    RankedArtist(
        artist = Artist(
            id = 4L,
            name = "오해원",
            profileImageUrl = "https://picsum.photos/seed/haewon/200",
            groupName = "NMIXX",
        ),
        rank = 4,
        rankDelta = null,
        isSubscribed = false,
    ),
)
