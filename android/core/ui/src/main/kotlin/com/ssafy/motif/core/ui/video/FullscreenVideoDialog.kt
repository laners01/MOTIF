package com.ssafy.motif.core.ui.video

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * 영상을 화면 전체로 보여주는 다이얼로그.
 *
 * - 열리는 동안 기기를 가로로 고정하고, 닫히면 이전 방향으로 되돌린다.
 *   Activity가 재생성되지 않도록 manifest에 configChanges(orientation|screenSize...)가 선언되어 있어야 한다.
 * - 시스템 바(상태바·내비게이션 바)를 숨긴다. 가장자리를 스와이프하면 잠시 나타난다.
 * - 시스템 뒤로가기와 전체화면 버튼으로 닫힌다.
 *
 * 인라인 플레이어와 같은 [state]를 넘기면 재생 위치·음소거 상태가 그대로 이어진다.
 */
@Composable
fun FullscreenVideoDialog(
    state: ControllableVideoPlayerState,
    onDismiss: () -> Unit,
    aspectRatio: Float = 16f / 9f,
) {
    LockLandscapeOrientation()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        HideSystemBars()

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            // 화면이 영상보다 넓으면 높이를, 좁으면 폭을 기준으로 맞춰 잘리지 않게 한다.
            val screenRatio = maxWidth / maxHeight
            val fitModifier = if (screenRatio > aspectRatio) {
                Modifier.fillMaxHeight()
            } else {
                Modifier.fillMaxWidth()
            }

            ControllableVideoPlayer(
                state = state,
                onFullscreenClick = onDismiss,
                isFullscreen = true,
                modifier = fitModifier.aspectRatio(aspectRatio),
            )
        }
    }
}

/**
 * 컴포지션에 있는 동안 가로 방향을 요청하고, 벗어나면 이전 요청값으로 되돌린다.
 */
@Composable
private fun LockLandscapeOrientation() {
    val activity = LocalContext.current.findActivity()

    DisposableEffect(activity) {
        if (activity == null) return@DisposableEffect onDispose {}

        val previousOrientation = activity.requestedOrientation
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

        onDispose {
            activity.requestedOrientation = previousOrientation
        }
    }
}

/**
 * 다이얼로그 윈도우의 시스템 바를 숨긴다. 다이얼로그가 닫히면 윈도우가 함께 사라져 별도 복원이 필요 없다.
 */
@Composable
private fun HideSystemBars() {
    val view = LocalView.current

    DisposableEffect(view) {
        val window = (view.parent as? DialogWindowProvider)?.window
            ?: return@DisposableEffect onDispose {}

        WindowCompat.setDecorFitsSystemWindows(window, false)

        val controller = WindowCompat.getInsetsController(window, view)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())

        onDispose {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
