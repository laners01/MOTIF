package com.ssafy.motif.feature.motif.impl.archive.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.ssafy.motif.core.designsystem.theme.MotifTheme
import com.ssafy.motif.core.model.motif.SavedMotif
import com.ssafy.motif.core.ui.motif.MotifVideoSection
import com.ssafy.motif.feature.motif.impl.archive.ArchiveMockData

/**
 * 보관함 상단의 재생 영역. 선택된 [motif]를 자동 재생한다.
 *
 * TODO 소리·재생 컨트롤·전체화면은 영상 플레이어 티켓에서 별도 컴포넌트로 교체 예정.
 *      그때까지는 마이페이지·아티스트 상세와 같은 MotifVideoSection(무음·반복)을 쓴다.
 */
@Composable
internal fun ArchivePlayer(
    motif: SavedMotif,
    modifier: Modifier = Modifier,
) {
    // 선택이 바뀌면 플레이어를 새로 만들어 새 영상을 처음부터 재생한다.
    key(motif.id) {
        MotifVideoSection(
            videoId = motif.id.toString(),
            videoUrl = motif.videoUrl,
            thumbnailUrl = motif.thumbnailUrl,
            modifier = modifier,
        )
    }
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
