package com.ssafy.motif.feature.mypage.impl.mypage.component

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import coil.compose.AsyncImage
import kotlin.math.absoluteValue
import kotlin.math.sign


@Composable
internal fun LikedChallengeCarousel(
    itemCount: Int,
    thumbnailUrl: (index: Int) -> String,
    onSettledPageChange: (index: Int) -> Unit,
    modifier: Modifier = Modifier,
    itemWidthFraction: Float = 232f / 402f,
    aspectRatio: Float = 232f / 352f,
    pageSpacing: Dp = 12.dp,
    minScale: Float = 0.88f,
) {
    if (itemCount <= 0) return

    val pagerState = rememberPagerState(
        pageCount = { itemCount },
    )

    // 스와이프 중이 아니라 스냅이 끝난 뒤에만 알린다.
    LaunchedEffect(pagerState.settledPage) {
        onSettledPageChange(pagerState.settledPage)
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxWidth(),
    ) {
        val itemWidth = maxWidth * itemWidthFraction

        val horizontalPadding = (maxWidth - itemWidth) / 2

        HorizontalPager(
            state = pagerState,
            pageSize = PageSize.Fixed(itemWidth),
            contentPadding = PaddingValues(horizontal = horizontalPadding),
            pageSpacing = pageSpacing,
        ) { page ->
            AsyncImage(
                model = thumbnailUrl(page),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(itemWidth)
                    .aspectRatio(aspectRatio)
                    .graphicsLayer {
                        val offset =
                            (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction

                        val distance = offset
                            .absoluteValue
                            .coerceIn(0f, 1f)

                        val scale = lerp(
                            start = minScale,
                            stop = 1f,
                            fraction = 1f - distance,
                        )

                        scaleX = scale
                        scaleY = scale


                        translationX = offset.sign *
                                itemWidth.toPx() *
                                (1f - scale) /
                                2f
                    }

            )
        }
    }
}
