package com.ssafy.motif.feature.artistdetail.impl.artistdetail.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ssafy.motif.core.model.artistdetail.ArtistMotif
import com.ssafy.motif.core.ui.video.VideoCarousel

@Composable
internal fun MotifVideoSection(
    motif: ArtistMotif,
    modifier: Modifier = Modifier,
) {
    VideoCarousel(
        itemCount = 1,
        videoId = { motif.id.toString() },
        videoUrl = { motif.videoUrl },
        thumbnailUrl = { motif.thumbnailUrl },
        onItemClick = { },
        modifier = modifier,
        itemWidthFraction = 1f,      // 기본 0.8f는 캐러셀용 여백
        aspectRatio = 16f / 9f,
        minScale = 1f,               // 기본 0.88f는 좌우 카드 축소용
        muted = true,
        repeat = true,
    )
}