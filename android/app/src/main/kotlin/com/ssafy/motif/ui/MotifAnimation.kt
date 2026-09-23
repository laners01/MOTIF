package com.ssafy.motif.ui

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import kotlin.math.roundToInt

private const val PUSH_OFFSET_RATIO = 0.10f
private const val BACKGROUND_OFFSET_RATIO = 0.025f
private const val TRANSITION_SCALE = 0.985f
private const val BACKGROUND_ALPHA = 0.94f

private const val PUSH_ENTER_DURATION_MILLIS = 280
private const val PUSH_EXIT_DURATION_MILLIS = 220
private const val POP_DURATION_MILLIS = 240

private const val PUSH_FADE_IN_DURATION_MILLIS = 220
private const val POP_FADE_IN_DURATION_MILLIS = 200
private const val FADE_OUT_DURATION_MILLIS = 180

private fun Int.offsetBy(ratio: Float): Int =
    (this * ratio).roundToInt()

internal fun motifPushTransition(): ContentTransform {
    val enter =
        slideInHorizontally(
            initialOffsetX = { width ->
                width.offsetBy(PUSH_OFFSET_RATIO)
            },
            animationSpec = tween(
                durationMillis = PUSH_ENTER_DURATION_MILLIS,
                easing = FastOutSlowInEasing,
            ),
        ) + fadeIn(
            animationSpec = tween(
                durationMillis = PUSH_FADE_IN_DURATION_MILLIS,
            ),
        ) + scaleIn(
            initialScale = TRANSITION_SCALE,
            animationSpec = tween(
                durationMillis = PUSH_ENTER_DURATION_MILLIS,
            ),
        )

    val exit =
        slideOutHorizontally(
            targetOffsetX = { width ->
                -width.offsetBy(BACKGROUND_OFFSET_RATIO)
            },
            animationSpec = tween(
                durationMillis = PUSH_EXIT_DURATION_MILLIS,
            ),
        ) + fadeOut(
            targetAlpha = BACKGROUND_ALPHA,
            animationSpec = tween(
                durationMillis = FADE_OUT_DURATION_MILLIS,
            ),
        )

    return enter togetherWith exit
}

internal fun motifPopTransition(): ContentTransform {
    val enter =
        slideInHorizontally(
            initialOffsetX = { width ->
                -width.offsetBy(BACKGROUND_OFFSET_RATIO)
            },
            animationSpec = tween(
                durationMillis = POP_DURATION_MILLIS,
            ),
        ) + fadeIn(
            initialAlpha = BACKGROUND_ALPHA,
            animationSpec = tween(
                durationMillis = POP_FADE_IN_DURATION_MILLIS,
            ),
        )

    val exit =
        slideOutHorizontally(
            targetOffsetX = { width ->
                width.offsetBy(PUSH_OFFSET_RATIO)
            },
            animationSpec = tween(
                durationMillis = POP_DURATION_MILLIS,
            ),
        ) + fadeOut(
            animationSpec = tween(
                durationMillis = FADE_OUT_DURATION_MILLIS,
            ),
        ) + scaleOut(
            targetScale = TRANSITION_SCALE,
            animationSpec = tween(
                durationMillis = POP_DURATION_MILLIS,
            ),
        )

    return enter togetherWith exit
}

internal fun motifPredictivePopTransition(): ContentTransform {
    val enter =
        slideInHorizontally(
            initialOffsetX = { width ->
                -width.offsetBy(BACKGROUND_OFFSET_RATIO)
            },
        ) + fadeIn(
            initialAlpha = BACKGROUND_ALPHA,
        )

    val exit =
        slideOutHorizontally(
            targetOffsetX = { width ->
                width.offsetBy(PUSH_OFFSET_RATIO)
            },
        ) + fadeOut() + scaleOut(
            targetScale = TRANSITION_SCALE,
        )

    return enter togetherWith exit
}