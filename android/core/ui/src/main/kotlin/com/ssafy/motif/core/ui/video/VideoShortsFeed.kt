package com.ssafy.motif.core.ui.video

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.ssafy.motif.core.designsystem.glass.GlassBackdropState
import com.ssafy.motif.core.designsystem.theme.MotifTheme

@Composable
fun VideoShortsFeed(
    itemCount: Int,
    videoId: (index: Int) -> String,
    videoUrl: (index: Int) -> String,
    thumbnailUrl: (index: Int) -> String,
    modifier: Modifier = Modifier,
    muted: Boolean = false,
    repeat: Boolean = true,
    captureBackdrop: Boolean = false,
    decoration: @Composable BoxScope.(
        index: Int,
        selected: Boolean,
        backdropState: GlassBackdropState,
    ) -> Unit = { _, _, _ -> },
) {
    if (itemCount <= 0) return

    val pagerState = rememberPagerState(
        pageCount = { itemCount },
    )

    val mediaItems =
        rememberVideoMediaItems(
            itemCount = itemCount,
            videoId = videoId,
            videoUrl = videoUrl,
        )

    val mediaController =
        rememberVideoMediaController(
            mediaItems = mediaItems,
            muted = muted,
            repeat = repeat,
        )

    LaunchedEffect(
        pagerState.settledPage,
        mediaController,
    ) {
        mediaController?.play(
            index = pagerState.settledPage,
        )
    }

    VerticalPager(
        state = pagerState,
        modifier = modifier.fillMaxSize(),
    ) { page ->
        val selected =
            pagerState.settledPage == page

        VideoShortsItem(
            thumbnailUrl = thumbnailUrl(page),
            selected = selected,
            player = mediaController?.player,
            captureBackdrop = captureBackdrop,
            modifier = Modifier.fillMaxSize(),
            decoration = { backdropState ->
                decoration(
                    page,
                    selected,
                    backdropState,
                )
            },
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun VideoShortsFeedPreview() {
    MotifTheme {
        VideoShortsFeed(
            itemCount = previewVideoUrls.size,
            videoId = { index ->
                index.toString()
            },
            videoUrl = { index ->
                previewVideoUrls[index]
            },
            thumbnailUrl = { index ->
                previewThumbnailUrls[index]
            },
            modifier = Modifier.fillMaxSize(),
            muted = true,
            repeat = true,
            captureBackdrop = false,
        )
    }
}

private val previewVideoUrls = listOf(
    "https://res.cloudinary.com/demo/video/upload/w_540,h_960,c_fill/kitten_fighting.mp4",
    "https://res.cloudinary.com/demo/video/upload/w_540,h_960,c_fill/dog.mp4",
    "https://res.cloudinary.com/demo/video/upload/w_540,h_960,c_fill/pencil_sketch.mp4",
    "https://res.cloudinary.com/demo/video/upload/w_540,h_960,c_fill/turtle.mp4",
    "https://res.cloudinary.com/demo/video/upload/w_540,h_960,c_fill/elephants.mp4",
)

private val previewThumbnailUrls = listOf(
    "https://res.cloudinary.com/demo/video/upload/so_1,w_540,h_960,c_fill/kitten_fighting.jpg",
    "https://res.cloudinary.com/demo/video/upload/so_1,w_540,h_960,c_fill/dog.jpg",
    "https://res.cloudinary.com/demo/video/upload/so_1,w_540,h_960,c_fill/pencil_sketch.jpg",
    "https://res.cloudinary.com/demo/video/upload/so_1,w_540,h_960,c_fill/turtle.jpg",
    "https://res.cloudinary.com/demo/video/upload/so_1,w_540,h_960,c_fill/elephants.jpg",
)