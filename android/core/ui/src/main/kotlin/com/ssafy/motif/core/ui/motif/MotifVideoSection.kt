package com.ssafy.motif.core.ui.motif

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ssafy.motif.core.ui.video.VideoCarousel

/**
 * 단일 모티프 영상을 자동 재생한다.
 *
 * 진입 시 자동 재생, 종료 시 반복, 로딩 중 썸네일 표시는 [VideoCarousel]이 처리한다.
 */
@Composable
fun MotifVideoSection(
    videoId: String,
    videoUrl: String,
    thumbnailUrl: String,
    modifier: Modifier = Modifier,
) {
    VideoCarousel(
        itemCount = 1,
        videoId = { videoId },
        videoUrl = { videoUrl },
        thumbnailUrl = { thumbnailUrl },
        onItemClick = { },
        modifier = modifier,
        itemWidthFraction = 1f,      // 기본 0.8f는 캐러셀용 여백
        aspectRatio = 16f / 9f,
        minScale = 1f,               // 기본 0.88f는 좌우 카드 축소용
        muted = true,
        repeat = true,
    )
}
