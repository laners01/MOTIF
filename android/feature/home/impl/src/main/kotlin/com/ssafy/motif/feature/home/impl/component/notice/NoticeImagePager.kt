package com.ssafy.motif.feature.home.impl.component.notice

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ssafy.motif.core.designsystem.component.GlassSurface
import com.ssafy.motif.core.designsystem.glass.glassBackdrop
import com.ssafy.motif.core.designsystem.glass.rememberGlassBackdropState

@Composable
internal fun NoticeImagePager(
    imageUrls: List<String>,
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState(
        pageCount = {
            imageUrls.size
        },
    )

    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp),
        ) { page ->
            NoticeImage(
                imageUrl = imageUrls[page],
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (imageUrls.size > 1) {
            NoticePageIndicator(
                pageCount = imageUrls.size,
                currentPage = pagerState.currentPage,
            )
        }
    }
}

@Composable
private fun NoticeImage(
    imageUrl: String,
    modifier: Modifier = Modifier,
) {
    val backdropState =
        rememberGlassBackdropState()

    Box(
        modifier = modifier,
    ) {
        AsyncImage(
            model = imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .matchParentSize()
                .glassBackdrop(
                    backdropState,
                ),
        )

        GlassSurface(
            backdropState = backdropState,
            modifier = Modifier.matchParentSize(),
            shape = RectangleShape,
            blurRadius = 16.dp,
            blurAlpha = 1f,
            tintColor = Color.Black.copy(
                alpha = 0.08f,
            ),
            borderWidth = 0.dp,
        ) {}

        AsyncImage(
            model = imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.matchParentSize(),
        )
    }
}

@Composable
private fun NoticePageIndicator(
    pageCount: Int,
    currentPage: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(32.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(pageCount) { index ->
            Box(
                modifier = Modifier
                    .padding(
                        horizontal = 3.dp,
                    )
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(
                        color =
                            if (index == currentPage) {
                                NoticeDefaults.AccentColor
                            } else {
                                Color.White.copy(
                                    alpha = 0.5f,
                                )
                            },
                    ),
            )
        }
    }
}