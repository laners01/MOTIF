package com.ssafy.motif.core.ui.video

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.source.preload.DefaultPreloadManager
import androidx.media3.exoplayer.source.preload.TargetPreloadStatusControl

@OptIn(UnstableApi::class)
internal class VideoPreloadStatusControl(
    var currentPlayingIndex: Int = 0,
) : TargetPreloadStatusControl<
        Int,
        DefaultPreloadManager.PreloadStatus,
        > {

    override fun getTargetPreloadStatus(
        index: Int,
    ): DefaultPreloadManager.PreloadStatus {
        val offset =
            index -
                    currentPlayingIndex

        return when (offset) {
            /*
             * 바로 다음 영상.
             *
             * 사용자가 가장 높은 확률로 이동하는 영상이므로
             * 실제 재생 버퍼에 6초 분량을 미리 로드한다.
             *
             * HLS에서는 manifest + segment 요청이 필요하므로
             * 기존 3초보다 조금 더 적극적으로 준비한다.
             */
            1 -> {
                DefaultPreloadManager
                    .PreloadStatus
                    .specifiedRangeLoaded(
                        NEXT_PRELOAD_DURATION_MS,
                    )
            }

            /*
             * 다음다음 영상.
             *
             * 아직 메모리 버퍼까지 올릴 필요는 없지만,
             * 디스크 캐시에 미리 받아둔다.
             *
             * 현재 영상
             *     ↓
             * +1 → memory preload
             * +2 → disk precache
             *
             * 사용자가 +1로 이동하면 기존 +2는 새로운 +1이 되고,
             * 디스크에 받아둔 데이터를 이용해 memory preload를
             * 빠르게 진행할 수 있다.
             */
            2 -> {
                DefaultPreloadManager
                    .PreloadStatus
                    .specifiedRangeCached(
                        NEXT_PRECACHE_DURATION_MS,
                    )
            }

            /*
             * 바로 이전 영상.
             *
             * 위로 다시 스와이프했을 때 빠르게 복귀할 수 있도록
             * 3초 정도만 메모리에 유지한다.
             */
            -1 -> {
                DefaultPreloadManager
                    .PreloadStatus
                    .specifiedRangeLoaded(
                        PREVIOUS_PRELOAD_DURATION_MS,
                    )
            }

            /*
             * 이전 두 번째 영상은
             * track 정보까지만 준비한다.
             */
            -2 -> {
                DefaultPreloadManager
                    .PreloadStatus
                    .PRELOAD_STATUS_TRACKS_SELECTED
            }

            /*
             * 현재 위치에서 조금 떨어진 영상.
             *
             * HLS manifest 등의 source까지만 준비한다.
             */
            3,
            -3,
            4,
            -4,
                -> {
                DefaultPreloadManager
                    .PreloadStatus
                    .PRELOAD_STATUS_SOURCE_PREPARED
            }

            else -> {
                DefaultPreloadManager
                    .PreloadStatus
                    .PRELOAD_STATUS_NOT_PRELOADED
            }
        }
    }

    private companion object {

        /*
         * 바로 다음 HLS 영상.
         *
         * 기존 3초 → 6초.
         */
        const val NEXT_PRELOAD_DURATION_MS =
            6_000L

        /*
         * 다음다음 영상은 디스크에 6초 precache.
         */
        const val NEXT_PRECACHE_DURATION_MS =
            6_000L

        /*
         * 이전 영상 복귀용.
         */
        const val PREVIOUS_PRELOAD_DURATION_MS =
            3_000L
    }
}