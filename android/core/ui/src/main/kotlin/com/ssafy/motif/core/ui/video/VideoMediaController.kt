package com.ssafy.motif.core.ui.video

import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.preload.DefaultPreloadManager

@OptIn(UnstableApi::class)
internal class VideoMediaController(
    val player: ExoPlayer,
    private val preloadManager: DefaultPreloadManager,
    private val preloadStatusControl: VideoPreloadStatusControl,
) {

    /*
     * 현재 PreloadManager에 등록되어 있는 MediaItem 목록.
     *
     * pagination으로 목록이 늘어나더라도
     * Controller 자체를 새로 만들지 않고
     * 이 목록만 업데이트한다.
     */
    private var mediaItems:
            List<MediaItem> =
        emptyList()

    /*
     * 현재 재생 중인 index.
     *
     * 목록이 변경될 때 같은 videoId의 위치를 찾아
     * 최대한 현재 위치를 유지한다.
     */
    private var currentPlayingIndex =
        0

    /**
     * 새로운 영상 목록을 PreloadManager에 반영한다.
     *
     * 1. 동일한 목록
     *    -> 아무 작업도 하지 않는다.
     *
     * 2. 기존 목록 뒤에 새로운 영상만 append
     *    -> 기존 preload를 유지하고 새 영상만 추가한다.
     *
     * 3. 삭제 / 순서 변경 / URL 변경
     *    -> 목록 구조 자체가 변경된 것이므로 reset 후 재등록한다.
     */
    fun updateMediaItems(
        newMediaItems: List<MediaItem>,
    ) {
        if (
            hasSameMediaItems(
                first =
                    mediaItems,
                second =
                    newMediaItems,
            )
        ) {
            return
        }

        val resolvedCurrentPlayingIndex =
            resolveCurrentPlayingIndex(
                oldMediaItems =
                    mediaItems,
                newMediaItems =
                    newMediaItems,
                oldCurrentPlayingIndex =
                    currentPlayingIndex,
            )

        val appendOnly =
            isAppendOnlyUpdate(
                oldMediaItems =
                    mediaItems,
                newMediaItems =
                    newMediaItems,
            )

        /*
         * addMediaItems() 자체가 preload 우선순위를 계산하므로
         * 먼저 TargetPreloadStatusControl이 바라보는
         * 현재 index를 갱신한다.
         */
        preloadStatusControl.currentPlayingIndex =
            resolvedCurrentPlayingIndex

        when {
            newMediaItems.isEmpty() -> {
                /*
                 * 전체 목록이 비었을 때만 reset.
                 */
                preloadManager.reset()
            }

            mediaItems.isEmpty() -> {
                /*
                 * 최초 등록.
                 */
                preloadManager.addMediaItems(
                    newMediaItems,
                    newMediaItems.indices
                        .toList(),
                )
            }

            appendOnly -> {
                /*
                 * pagination.
                 *
                 * 예:
                 *
                 * 기존
                 * 0..19
                 *
                 * 새로운 목록
                 * 0..39
                 *
                 * 여기서 reset하지 않고
                 * 20..39만 추가한다.
                 */
                val startIndex =
                    mediaItems.size

                val appendedMediaItems =
                    newMediaItems.subList(
                        startIndex,
                        newMediaItems.size,
                    )

                val appendedRankingData =
                    (
                            startIndex until
                                    newMediaItems.size
                            )
                        .toList()

                preloadManager.addMediaItems(
                    appendedMediaItems,
                    appendedRankingData,
                )
            }

            else -> {
                /*
                 * 단순 append가 아닌 경우.
                 *
                 * 삭제 / 순서 변경 / URL 변경 등이 발생한 경우에는
                 * rankingData(index)가 달라질 수 있으므로
                 * 전체 목록을 다시 구성한다.
                 */
                preloadManager.reset()

                preloadManager.addMediaItems(
                    newMediaItems,
                    newMediaItems.indices
                        .toList(),
                )
            }
        }

        mediaItems =
            newMediaItems

        currentPlayingIndex =
            resolvedCurrentPlayingIndex

        if (
            newMediaItems.isNotEmpty()
        ) {
            /*
             * PreloadManager에도 현재 재생 위치를 알려서
             * +1 / +2 우선순위를 다시 계산하게 한다.
             */
            preloadManager.setCurrentPlayingIndex(
                currentPlayingIndex,
            )
        }
    }

    fun play(
        index: Int,
    ) {
        val mediaItem =
            mediaItems
                .getOrNull(
                    index,
                )
                ?: return

        /*
         * 앞으로 preload할 영상들의 우선순위를
         * 새 현재 위치 기준으로 변경한다.
         */
        currentPlayingIndex =
            index

        preloadStatusControl.currentPlayingIndex =
            index

        preloadManager.setCurrentPlayingIndex(
            index,
        )

        /*
         * 이미 동일한 영상을 Player가 가지고 있는 경우
         * 다시 setMediaSource() + prepare() 하지 않는다.
         *
         * 불필요한 재버퍼링 방지.
         */
        val currentMediaItem =
            player.currentMediaItem

        if (
            currentMediaItem != null &&
            isSameMediaItem(
                first =
                    currentMediaItem,
                second =
                    mediaItem,
            )
        ) {
            if (
                player.playbackState ==
                Player.STATE_IDLE
            ) {
                player.prepare()
            }

            player.play()

            return
        }

        /*
         * PreloadManager가 이미 가지고 있는 MediaSource를
         * 그대로 Player에 넘긴다.
         *
         * +1 영상이 preload 완료되어 있다면
         * 준비된 MediaSource를 이용할 수 있다.
         */
        val mediaSource =
            preloadManager.getMediaSource(
                mediaItem,
            )

        if (
            mediaSource != null
        ) {
            player.setMediaSource(
                mediaSource,
            )
        } else {
            /*
             * 아직 PreloadManager에 등록되지 않은 영상이라면
             * 일반 MediaItem 방식으로 fallback.
             */
            player.setMediaItem(
                mediaItem,
            )
        }

        player.prepare()

        player.play()
    }

    /**
     * 현재 재생 상태를 반전하고
     * 변경 이후 재생 상태를 반환한다.
     *
     * true  -> 재생
     * false -> 일시정지
     */
    fun togglePlayback():
            Boolean {
        val shouldPlay =
            !player.playWhenReady

        if (
            shouldPlay
        ) {
            player.play()
        } else {
            player.pause()
        }

        return shouldPlay
    }

    fun seekTo(
        progress: Float,
    ) {
        val duration =
            player.duration

        if (
            duration <=
            0L
        ) {
            return
        }

        val position =
            (
                    duration *
                            progress.coerceIn(
                                minimumValue =
                                    0f,
                                maximumValue =
                                    1f,
                            )
                    )
                .toLong()

        player.seekTo(
            position,
        )
    }

    fun playbackProgress():
            Float {
        val duration =
            player.duration

        if (
            duration <=
            0L
        ) {
            return 0f
        }

        return (
                player.currentPosition
                    .toFloat() /
                        duration.toFloat()
                )
            .coerceIn(
                minimumValue =
                    0f,
                maximumValue =
                    1f,
            )
    }

    private fun isAppendOnlyUpdate(
        oldMediaItems: List<MediaItem>,
        newMediaItems: List<MediaItem>,
    ): Boolean {
        if (
            oldMediaItems.isEmpty() ||
            newMediaItems.size <=
            oldMediaItems.size
        ) {
            return false
        }

        return oldMediaItems
            .indices
            .all { index ->
                isSameMediaItem(
                    first =
                        oldMediaItems[index],
                    second =
                        newMediaItems[index],
                )
            }
    }

    private fun resolveCurrentPlayingIndex(
        oldMediaItems: List<MediaItem>,
        newMediaItems: List<MediaItem>,
        oldCurrentPlayingIndex: Int,
    ): Int {
        if (
            newMediaItems.isEmpty()
        ) {
            return 0
        }

        val currentMediaId =
            oldMediaItems
                .getOrNull(
                    oldCurrentPlayingIndex,
                )
                ?.mediaId

        if (
            currentMediaId !=
            null
        ) {
            val newIndex =
                newMediaItems
                    .indexOfFirst { mediaItem ->
                        mediaItem.mediaId ==
                                currentMediaId
                    }

            if (
                newIndex >=
                0
            ) {
                return newIndex
            }
        }

        return oldCurrentPlayingIndex
            .coerceIn(
                minimumValue =
                    0,
                maximumValue =
                    newMediaItems.lastIndex,
            )
    }

    private fun hasSameMediaItems(
        first: List<MediaItem>,
        second: List<MediaItem>,
    ): Boolean {
        if (
            first.size !=
            second.size
        ) {
            return false
        }

        return first
            .indices
            .all { index ->
                isSameMediaItem(
                    first =
                        first[index],
                    second =
                        second[index],
                )
            }
    }

    private fun isSameMediaItem(
        first: MediaItem,
        second: MediaItem,
    ): Boolean {
        return first.mediaId ==
                second.mediaId &&
                first.localConfiguration
                    ?.uri ==
                second.localConfiguration
                    ?.uri
    }
}

@Composable
internal fun rememberVideoMediaItems(
    itemCount: Int,
    videoId: (
        index: Int,
    ) -> String,
    videoUrl: (
        index: Int,
    ) -> String,
): List<MediaItem> {
    val mediaSpecs =
        List(
            itemCount,
        ) { index ->
            VideoMediaSpec(
                id =
                    videoId(
                        index,
                    ),
                url =
                    videoUrl(
                        index,
                    ),
            )
        }

    return remember(
        mediaSpecs,
    ) {
        mediaSpecs
            .map { spec ->
                MediaItem
                    .Builder()
                    .setMediaId(
                        spec.id,
                    )
                    .setUri(
                        spec.url,
                    )
                    .build()
            }
    }
}

@OptIn(UnstableApi::class)
@Composable
internal fun rememberVideoMediaController(
    mediaItems: List<MediaItem>,
    muted: Boolean,
    repeat: Boolean,
): VideoMediaController? {
    if (
        LocalInspectionMode.current
    ) {
        return null
    }

    val context =
        LocalContext.current
            .applicationContext

    /*
     * 앱 전체에서 하나의 SimpleCache를 공유한다.
     */
    val cache =
        remember(
            context,
        ) {
            VideoMediaCache.getInstance(
                context,
            )
        }

    val preloadStatusControl =
        remember {
            VideoPreloadStatusControl()
        }

    /*
     * 동일 Builder를 이용해
     *
     * DefaultPreloadManager
     * ExoPlayer
     *
     * 를 생성한다.
     *
     * 이를 통해 동일한 media/cache 설정을 공유한다.
     */
    val preloadManagerBuilder =
        remember(
            context,
            cache,
            preloadStatusControl,
        ) {
            DefaultPreloadManager
                .Builder(
                    context,
                    preloadStatusControl,
                )
                .setCache(
                    cache,
                )
        }

    val preloadManager =
        remember(
            preloadManagerBuilder,
        ) {
            preloadManagerBuilder
                .build()
        }

    val player =
        remember(
            preloadManagerBuilder,
        ) {
            preloadManagerBuilder
                .buildExoPlayer()
        }

    /*
     * 음소거 / 반복 설정은
     * Controller나 Player를 새로 만들지 않고
     * 기존 Player에 반영한다.
     */
    SideEffect {
        player.volume =
            if (
                muted
            ) {
                0f
            } else {
                1f
            }

        player.repeatMode =
            if (
                repeat
            ) {
                Player.REPEAT_MODE_ONE
            } else {
                Player.REPEAT_MODE_OFF
            }
    }

    /*
     * 중요:
     *
     * mediaItems를 remember key에서 제거했다.
     *
     * pagination으로
     *
     * 20개 -> 40개
     *
     * 가 되더라도 VideoMediaController 객체 자체는
     * 다시 생성되지 않는다.
     */
    val controller =
        remember(
            player,
            preloadManager,
            preloadStatusControl,
        ) {
            VideoMediaController(
                player =
                    player,
                preloadManager =
                    preloadManager,
                preloadStatusControl =
                    preloadStatusControl,
            )
        }

    /*
     * mediaItems가 변경되면 Controller 내부 목록만 갱신한다.
     *
     * Controller가 append 여부를 판단해서
     *
     * append:
     *     새로운 item만 PreloadManager에 추가
     *
     * 구조 변경:
     *     reset 후 재등록
     *
     * 을 결정한다.
     */
    DisposableEffect(
        controller,
        mediaItems,
    ) {
        controller.updateMediaItems(
            newMediaItems =
                mediaItems,
        )

        onDispose {}
    }

    /*
     * 화면에서 완전히 사라질 때만
     * Player / PreloadManager를 release한다.
     *
     * pagination에서는 실행되지 않는다.
     */
    DisposableEffect(
        player,
        preloadManager,
    ) {
        onDispose {
            player.release()

            preloadManager.release()
        }
    }

    return controller
}

private data class VideoMediaSpec(
    val id: String,
    val url: String,
)