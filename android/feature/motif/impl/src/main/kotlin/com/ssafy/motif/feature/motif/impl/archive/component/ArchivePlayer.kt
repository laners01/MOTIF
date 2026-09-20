package com.ssafy.motif.feature.motif.impl.archive.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.ssafy.motif.core.designsystem.theme.MotifTheme
import com.ssafy.motif.core.model.motif.SavedMotif
import com.ssafy.motif.feature.motif.impl.archive.ArchiveMockData
import com.ssafy.motif.feature.motif.impl.component.MotifVideoPlayer

/**
 * 보관함 상단의 재생 영역. 선택된 [motif]를 소리 있게 자동 재생하고, 전체화면 진입을 처리한다.
 */
@Composable
internal fun ArchivePlayer(
    motif: SavedMotif,
    modifier: Modifier = Modifier,
) {
    MotifVideoPlayer(
        videoId = motif.id.toString(),
        videoUrl = motif.videoUrl,
        thumbnailUrl = motif.thumbnailUrl,
        modifier = modifier,
    )
}

@Preview(showBackground = true)
@Composable
private fun ArchivePlayerPreview() {
    MotifTheme {
        ArchivePlayer(
            motif = ArchiveMockData.savedMotifs.first(),
        )
    }
}
