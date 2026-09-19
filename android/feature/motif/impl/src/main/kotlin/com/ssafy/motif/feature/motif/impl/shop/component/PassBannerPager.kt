package com.ssafy.motif.feature.motif.impl.shop.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
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
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ssafy.motif.core.designsystem.theme.MotifTheme
import com.ssafy.motif.core.model.shop.PassBanner
import com.ssafy.motif.feature.motif.impl.shop.ShopPreviewFixtures
import kotlinx.coroutines.delay

/**
 * 구독권 프로모션 배너 가로 페이저.
 * 배너가 여러 개면 점 인디케이터를 표시하고, 사용자가 드래그하지 않는 동안 자동으로 넘긴다.
 */
@Composable
internal fun PassBannerPager(
    banners: List<PassBanner>,
    onBannerClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState(
        pageCount = { banners.size },
    )

    AutoScrollEffect(
        pagerState = pagerState,
        pageCount = banners.size,
    )

    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(BannerAspectRatio),
        ) { page ->
            val banner = banners[page]

            AsyncImage(
                model = banner.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(role = Role.Button) { onBannerClick(banner.id) },
            )
        }

        if (banners.size > 1) {
            PassBannerIndicator(
                pageCount = banners.size,
                currentPage = pagerState.currentPage,
            )
        }
    }
}

/**
 * [AutoScrollIntervalMillis]마다 다음 페이지로 넘긴다. 사용자가 드래그 중이면 멈추고, 손을 떼면 다시 시작한다.
 */
@Composable
private fun AutoScrollEffect(
    pagerState: PagerState,
    pageCount: Int,
) {
    val isDragged by pagerState.interactionSource.collectIsDraggedAsState()

    LaunchedEffect(isDragged, pageCount) {
        if (isDragged || pageCount <= 1) return@LaunchedEffect

        while (true) {
            delay(AutoScrollIntervalMillis)
            pagerState.animateScrollToPage((pagerState.currentPage + 1) % pageCount)
        }
    }
}

@Composable
private fun PassBannerIndicator(
    pageCount: Int,
    currentPage: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(24.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(pageCount) { index ->
            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(
                        color = if (index == currentPage) {
                            MotifTheme.colors.pink
                        } else {
                            MotifTheme.colors.gray500
                        },
                    ),
            )
        }
    }
}

private const val BannerAspectRatio = 8f / 3f
private const val AutoScrollIntervalMillis = 4_000L

@Preview(showBackground = true)
@Composable
private fun PassBannerPagerPreview() {
    MotifTheme {
        PassBannerPager(
            banners = ShopPreviewFixtures.banners(),
            onBannerClick = {},
        )
    }
}
