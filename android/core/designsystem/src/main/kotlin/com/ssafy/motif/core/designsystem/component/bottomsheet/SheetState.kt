package com.ssafy.motif.core.designsystem.component.bottomsheet

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember

enum class SheetValue {
    Hidden,
    PartiallyExpanded,
    Expanded,
}

@OptIn(ExperimentalFoundationApi::class)
@Stable
class SheetState internal constructor(
    val skipPartiallyExpanded: Boolean,
    val partiallyExpandedFraction: Float,
) {
    init {
        require(partiallyExpandedFraction in 0f..1f) {
            "partiallyExpandedFraction must be between 0f and 1f."
        }
    }

    internal val anchoredDraggableState =
        AnchoredDraggableState(
            initialValue = SheetValue.Hidden,
        )

    val currentValue: SheetValue
        get() = anchoredDraggableState.currentValue

    val targetValue: SheetValue
        get() = anchoredDraggableState.targetValue

    val isVisible: Boolean
        get() =
            currentValue != SheetValue.Hidden ||
                    targetValue != SheetValue.Hidden

    suspend fun show() {
        val partiallyExpandedPosition =
            anchoredDraggableState
                .anchors
                .positionOf(SheetValue.PartiallyExpanded)

        val target =
            if (
                !skipPartiallyExpanded &&
                !partiallyExpandedPosition.isNaN()
            ) {
                SheetValue.PartiallyExpanded
            } else {
                SheetValue.Expanded
            }

        animateTo(target)
    }

    suspend fun hide() {
        animateTo(SheetValue.Hidden)
    }

    suspend fun expand() {
        animateTo(SheetValue.Expanded)
    }

    suspend fun partialExpand() {
        if (skipPartiallyExpanded) {
            return
        }

        val position =
            anchoredDraggableState
                .anchors
                .positionOf(SheetValue.PartiallyExpanded)

        if (!position.isNaN()) {
            animateTo(SheetValue.PartiallyExpanded)
        }
    }

    private suspend fun animateTo(
        targetValue: SheetValue,
    ) {
        val targetPosition =
            anchoredDraggableState
                .anchors
                .positionOf(targetValue)

        if (targetPosition.isNaN()) {
            return
        }

        anchoredDraggableState.animateTo(
            targetValue = targetValue,
            animationSpec = BottomSheetDefaults.AnimationSpec,
        )
    }

    internal suspend fun settle(
        velocity: Float,
    ) {
        val state = anchoredDraggableState
        val offset = state.offset

        if (offset.isNaN()) {
            return
        }

        val partialPosition =
            state.anchors
                .positionOf(SheetValue.PartiallyExpanded)

        val hasPartialState =
            !skipPartiallyExpanded &&
                    !partialPosition.isNaN()

        val target =
            when {
                // 위로 fling
                velocity < 0f -> {
                    if (
                        hasPartialState &&
                        offset > partialPosition
                    ) {
                        SheetValue.PartiallyExpanded
                    } else {
                        SheetValue.Expanded
                    }
                }

                // 아래로 fling
                velocity > 0f -> {
                    if (
                        hasPartialState &&
                        offset < partialPosition
                    ) {
                        SheetValue.PartiallyExpanded
                    } else {
                        SheetValue.Hidden
                    }
                }

                else -> {
                    state.anchors.closestAnchor(offset)
                        ?: currentValue
                }
            }

        animateTo(target)
    }
}

@Composable
fun rememberModalBottomSheetState(
    skipPartiallyExpanded: Boolean = false,
    partiallyExpandedFraction: Float =
        BottomSheetDefaults.PARTIALLY_EXPANDED_FRACTION,
): SheetState =
    remember(
        skipPartiallyExpanded,
        partiallyExpandedFraction,
    ) {
        SheetState(
            skipPartiallyExpanded = skipPartiallyExpanded,
            partiallyExpandedFraction = partiallyExpandedFraction,
        )
    }