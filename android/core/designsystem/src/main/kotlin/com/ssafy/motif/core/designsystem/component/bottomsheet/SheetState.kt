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
        require(
            partiallyExpandedFraction in 0f..1f,
        ) {
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

    fun offsetOrNull(): Float? =
        anchoredDraggableState.offset
            .takeUnless { offset ->
                offset.isNaN()
            }

    suspend fun show() {
        val targetValue =
            if (
                !skipPartiallyExpanded &&
                anchorPosition(
                    SheetValue.PartiallyExpanded,
                ) != null
            ) {
                SheetValue.PartiallyExpanded
            } else {
                SheetValue.Expanded
            }

        animateTo(
            targetValue = targetValue,
        )
    }

    suspend fun hide() {
        animateTo(
            targetValue = SheetValue.Hidden,
        )
    }

    suspend fun expand() {
        animateTo(
            targetValue = SheetValue.Expanded,
        )
    }

    suspend fun partialExpand() {
        if (skipPartiallyExpanded) {
            return
        }

        animateTo(
            targetValue = SheetValue.PartiallyExpanded,
        )
    }

    internal fun anchorPosition(
        value: SheetValue,
    ): Float? =
        anchoredDraggableState
            .anchors
            .positionOf(value)
            .takeUnless { position ->
                position.isNaN()
            }

    internal fun nextDownValue(
        startValue: SheetValue,
    ): SheetValue? =
        when (startValue) {
            SheetValue.Expanded -> {
                if (
                    !skipPartiallyExpanded &&
                    anchorPosition(
                        SheetValue.PartiallyExpanded,
                    ) != null
                ) {
                    SheetValue.PartiallyExpanded
                } else {
                    SheetValue.Hidden
                }
            }

            SheetValue.PartiallyExpanded ->
                SheetValue.Hidden

            SheetValue.Hidden ->
                null
        }

    internal suspend fun settleDown(
        startValue: SheetValue,
    ) {
        val offset =
            offsetOrNull()
                ?: return

        val nextValue =
            nextDownValue(
                startValue = startValue,
            ) ?: return

        val startPosition =
            anchorPosition(
                value = startValue,
            ) ?: return

        val nextPosition =
            anchorPosition(
                value = nextValue,
            ) ?: return

        val threshold =
            startPosition +
                    (
                            nextPosition - startPosition
                            ) * SETTLE_THRESHOLD_FRACTION

        animateTo(
            targetValue =
                if (offset >= threshold) {
                    nextValue
                } else {
                    startValue
                },
        )
    }

    private suspend fun animateTo(
        targetValue: SheetValue,
    ) {
        if (
            anchorPosition(
                value = targetValue,
            ) == null
        ) {
            return
        }

        anchoredDraggableState.animateTo(
            targetValue = targetValue,
            animationSpec =
                BottomSheetDefaults.AnimationSpec,
        )
    }
}

@Composable
fun rememberBottomSheetState(
    skipPartiallyExpanded: Boolean = false,
    partiallyExpandedFraction: Float =
        BottomSheetDefaults.PARTIALLY_EXPANDED_FRACTION,
): SheetState =
    remember(
        skipPartiallyExpanded,
        partiallyExpandedFraction,
    ) {
        SheetState(
            skipPartiallyExpanded =
                skipPartiallyExpanded,
            partiallyExpandedFraction =
                partiallyExpandedFraction,
        )
    }

private const val SETTLE_THRESHOLD_FRACTION = 0.1f