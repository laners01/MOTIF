package com.ssafy.motif.feature.home.impl.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ssafy.motif.core.designsystem.theme.MotifTheme
import com.ssafy.motif.core.model.home.Onstage
import com.ssafy.motif.core.ui.video.VideoCarousel
import com.ssafy.motif.core.ui.video.VideoSongInfo

@Composable
internal fun OnstageCarousel(
    items: List<Onstage>,
    onVideoClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    VideoCarousel(
        itemCount = items.size,
        videoId = { index -> items[index].videoId.toString() },
        videoUrl = { index -> items[index].videoUrl },
        thumbnailUrl = { index -> items[index].thumbnailUrl },
        onItemClick = { index ->
            onVideoClick(items[index].videoId)
        },
        modifier = modifier,
        itemWidthFraction = 0.8f,
        aspectRatio = 320f / 456f,
        muted = true,
        repeat = true,
        captureBackdrop = true,
        decoration = { index, _, backdropState ->
            val item = items[index]

            VideoSongInfo(
                songTitle = item.songTitle,
                artistName = item.songArtistName,
                backdropState = backdropState,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp),
            )
        },
    )
}

@Preview(showBackground = true)
@Composable
private fun OnstageCarouselPreview() {
    MotifTheme {
        Box(Modifier.fillMaxSize()) {
            OnstageCarousel(
                items = mockOnstageItems,
                onVideoClick = {},
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

private val mockOnstageItems = listOf(
    Onstage(
        videoId = 1L,
        rank = 1,
        videoUrl = "https://res.cloudinary.com/demo/video/upload/w_540,h_960,c_fill/kitten_fighting.mp4",
        thumbnailUrl = "https://res.cloudinary.com/demo/video/upload/so_1,w_540,h_960,c_fill/kitten_fighting.jpg",
        songArtistName = "mochico",
        songTitle = "bailar toda la noche",
    ),
    Onstage(
        videoId = 2L,
        rank = 2,
        videoUrl = "https://res.cloudinary.com/demo/video/upload/w_540,h_960,c_fill/dog.mp4",
        thumbnailUrl = "https://res.cloudinary.com/demo/video/upload/so_1,w_540,h_960,c_fill/dog.jpg",
        songArtistName = "aespa",
        songTitle = "Whiplash",
    ),
    Onstage(
        videoId = 3L,
        rank = 3,
        videoUrl = "https://res.cloudinary.com/demo/video/upload/w_540,h_960,c_fill/pencil_sketch.mp4",
        thumbnailUrl = "https://res.cloudinary.com/demo/video/upload/so_1,w_540,h_960,c_fill/pencil_sketch.jpg",
        songArtistName = "LE SSERAFIM",
        songTitle = "CRAZY",
    ),
    Onstage(
        videoId = 4L,
        rank = 4,
        videoUrl = "https://res.cloudinary.com/demo/video/upload/w_540,h_960,c_fill/turtle.mp4",
        thumbnailUrl = "https://res.cloudinary.com/demo/video/upload/so_1,w_540,h_960,c_fill/turtle.jpg",
        songArtistName = "IVE",
        songTitle = "ATTITUDE",
    ),
    Onstage(
        videoId = 5L,
        rank = 5,
        videoUrl = "https://res.cloudinary.com/demo/video/upload/w_540,h_960,c_fill/elephants.mp4",
        thumbnailUrl = "https://res.cloudinary.com/demo/video/upload/so_1,w_540,h_960,c_fill/elephants.jpg",
        songArtistName = "NMIXX",
        songTitle = "KNOW ABOUT ME",
    ),
)