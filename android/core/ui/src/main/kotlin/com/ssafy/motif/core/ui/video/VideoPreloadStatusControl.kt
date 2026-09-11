package com.ssafy.motif.core.ui.video

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.source.preload.DefaultPreloadManager
import androidx.media3.exoplayer.source.preload.TargetPreloadStatusControl

@OptIn(UnstableApi::class)
internal class VideoPreloadStatusControl(
    var currentPlayingIndex: Int = 0,
) : TargetPreloadStatusControl<Int, DefaultPreloadManager.PreloadStatus> {

    override fun getTargetPreloadStatus(
        index: Int,
    ): DefaultPreloadManager.PreloadStatus {
        val offset = index - currentPlayingIndex

        return when (offset) {
            // 다음 영상은 가장 적극적으로 준비
            1 -> {
                DefaultPreloadManager.PreloadStatus
                    .specifiedRangeLoaded(NEXT_PRELOAD_DURATION_MS)
            }

            // 이전 영상도 바로 돌아갈 수 있게 준비
            -1 -> {
                DefaultPreloadManager.PreloadStatus
                    .specifiedRangeLoaded(PREVIOUS_PRELOAD_DURATION_MS)
            }

            // 두 칸 떨어진 영상은 디코딩할 트랙까지만 미리 선택
            2, -2 -> {
                DefaultPreloadManager.PreloadStatus
                    .PRELOAD_STATUS_TRACKS_SELECTED
            }

            // 더 먼 영상은 source까지만 준비
            3, -3, 4, -4 -> {
                DefaultPreloadManager.PreloadStatus
                    .PRELOAD_STATUS_SOURCE_PREPARED
            }

            else -> {
                DefaultPreloadManager.PreloadStatus
                    .PRELOAD_STATUS_NOT_PRELOADED
            }
        }
    }

    private companion object {
        const val NEXT_PRELOAD_DURATION_MS = 3_000L
        const val PREVIOUS_PRELOAD_DURATION_MS = 2_000L
    }
}