package com.ssafy.motif.core.ui.video

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ssafy.motif.core.designsystem.glass.GlassBackdropState
import com.ssafy.motif.core.designsystem.theme.MotifTheme

@Composable
fun VideoCarousel(
    itemCount: Int,
    videoId: (index: Int) -> String,
    videoUrl: (index: Int) -> String,
    thumbnailUrl: (index: Int) -> String,
    onItemClick: (index: Int) -> Unit,
    modifier: Modifier = Modifier,
    itemWidthFraction: Float = 0.8f,
    aspectRatio: Float = 320f / 456f,
    pageSpacing: Dp = 12.dp,
    minScale: Float = 0.88f,
    muted: Boolean = true,
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

    BoxWithConstraints(
        modifier = modifier.fillMaxWidth(),
    ) {
        val itemWidth =
            maxWidth * itemWidthFraction

        val horizontalPadding =
            (maxWidth - itemWidth) / 2

        HorizontalPager(
            state = pagerState,
            pageSize = PageSize.Fixed(
                itemWidth,
            ),
            contentPadding = PaddingValues(
                horizontal = horizontalPadding,
            ),
            pageSpacing = pageSpacing,
        ) { page ->
            val selected = pagerState.settledPage == page
            VideoCarouselItem(
                thumbnailUrl = thumbnailUrl(page),
                selected = selected,
                pageOffset = {
                    (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                },
                itemWidth = itemWidth,
                aspectRatio = aspectRatio,
                minScale = minScale,
                player = mediaController?.player,
                captureBackdrop = captureBackdrop,
                onClick = {
                    onItemClick(page)
                },
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
}

@Preview(showBackground = true)
@Composable
private fun VideoCarouselPreview() {
    MotifTheme {
        Box(Modifier.fillMaxSize()) {
            VideoCarousel(
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
                onItemClick = {},
                modifier = Modifier.align(Alignment.Center),
                captureBackdrop = false,
            )
        }
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