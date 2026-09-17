package com.ssafy.motif.core.designsystem.component.bottomsheet

import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Velocity
import kotlin.math.roundToInt

internal fun SheetState.updateSheetAnchors(
    rootHeight: Int,
) {
    val rootHeightPx = rootHeight.toFloat()
    val partiallyExpandedOffset = rootHeightPx * (1f - partiallyExpandedFraction)

    anchoredDraggableState.updateAnchors(
        newAnchors = DraggableAnchors {
            SheetValue.Hidden at rootHeightPx

            if (!skipPartiallyExpanded) {
                SheetValue.PartiallyExpanded at partiallyExpandedOffset
            }

            SheetValue.Expanded at 0f
        },
    )
}

internal fun Modifier.sheetVisibleHeight(
    sheetState: SheetState,
): Modifier = layout { measurable, constraints ->
    val rootHeight = constraints.maxHeight
    val sheetTop = sheetState
        .offsetOrNull()
        ?.roundToInt()
        ?.coerceIn(0, rootHeight)
        ?: rootHeight

    val sheetHeight = rootHeight - sheetTop

    val placeable = measurable.measure(
        constraints.copy(
            minHeight = sheetHeight,
            maxHeight = sheetHeight,
        ),
    )

    layout(
        width = placeable.width,
        height = sheetHeight,
    ) {
        placeable.placeRelative(
            x = 0,
            y = 0,
        )
    }
}

internal fun sheetContentNestedScrollConnection(
    sheetState: SheetState,
): NestedScrollConnection =
    object : NestedScrollConnection {

        private var collapseStartValue: SheetValue? = null

        override fun onPreScroll(
            available: Offset,
            source: NestedScrollSource,
        ): Offset {
            val startValue = collapseStartValue ?: return Offset.Zero

            if (
                source != NestedScrollSource.UserInput ||
                available.y >= 0f
            ) {
                return Offset.Zero
            }

            val state = sheetState.anchoredDraggableState
            val offset = sheetState.offsetOrNull() ?: return Offset.Zero
            val startPosition = sheetState.anchorPosition(startValue) ?: return Offset.Zero

            if (offset <= startPosition) {
                collapseStartValue = null
                return Offset.Zero
            }

            val delta = available.y.coerceAtLeast(startPosition - offset)
            val consumed = state.dispatchRawDelta(delta)

            if (state.offset <= startPosition) {
                collapseStartValue = null
            }

            return Offset(
                x = 0f,
                y = consumed,
            )
        }

        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource,
        ): Offset {
            if (
                source != NestedScrollSource.UserInput ||
                available.y <= 0f ||
                sheetState.currentValue == SheetValue.Hidden
            ) {
                return Offset.Zero
            }

            val startValue = collapseStartValue
                ?: sheetState.currentValue.also {
                    collapseStartValue = it
                }

            val nextValue = sheetState.nextDownValue(startValue) ?: return Offset.Zero
            val nextPosition = sheetState.anchorPosition(nextValue) ?: return Offset.Zero
            val offset = sheetState.offsetOrNull() ?: return Offset.Zero

            val remainingDistance = (nextPosition - offset).coerceAtLeast(0f)
            val delta = available.y.coerceAtMost(remainingDistance)

            if (delta <= 0f) {
                return Offset.Zero
            }

            val consumedBySheet = sheetState.anchoredDraggableState.dispatchRawDelta(delta)

            return Offset(
                x = 0f,
                y = consumedBySheet,
            )
        }

        override suspend fun onPreFling(
            available: Velocity,
        ): Velocity {
            val startValue = collapseStartValue ?: return Velocity.Zero

            collapseStartValue = null
            sheetState.settleDown(startValue)

            return available
        }
    }

internal fun SheetState.scrimProgress(): Float {
    val offset = offsetOrNull() ?: return 0f
    val hiddenPosition = anchorPosition(SheetValue.Hidden) ?: return 0f
    val shownPosition = anchorPosition(SheetValue.PartiallyExpanded)
        ?: anchorPosition(SheetValue.Expanded)
        ?: return 0f

    val distance = hiddenPosition - shownPosition

    if (distance <= 0f) {
        return 0f
    }

    return ((hiddenPosition - offset) / distance).coerceIn(0f, 1f)
}

internal fun Modifier.scrimClickArea(
    sheetState: SheetState,
): Modifier = layout { measurable, constraints ->
    val height = sheetState
        .offsetOrNull()
        ?.roundToInt()
        ?.coerceIn(0, constraints.maxHeight)
        ?: 0

    val placeable = measurable.measure(
        constraints.copy(
            minHeight = height,
            maxHeight = height,
        ),
    )

    layout(
        width = placeable.width,
        height = height,
    ) {
        placeable.placeRelative(
            x = 0,
            y = 0,
        )
    }
}