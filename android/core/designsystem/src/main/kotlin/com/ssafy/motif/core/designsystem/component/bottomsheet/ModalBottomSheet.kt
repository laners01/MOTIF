package com.ssafy.motif.core.designsystem.component.bottomsheet

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.ssafy.motif.core.designsystem.component.Surface
import com.ssafy.motif.core.designsystem.theme.MotifTheme
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
    shape: Shape = BottomSheetDefaults.ExpandedShape,
    containerColor: Color = MotifTheme.colors.background,
    contentColor: Color = MotifTheme.colors.typo,
    sheetGesturesEnabled: Boolean = true,
    dragHandle: @Composable (() -> Unit)? = {
        BottomSheetDefaults.DragHandle()
    },
    content: @Composable ColumnScope.() -> Unit,
) {
    val currentOnDismissRequest by
    rememberUpdatedState(
        onDismissRequest,
    )

    var rootHeight by remember {
        mutableFloatStateOf(0f)
    }

    /*
     * Hidden
     *   -> 화면 아래
     *
     * PartiallyExpanded
     *   -> partiallyExpandedFraction 만큼 노출
     *
     * Expanded
     *   -> 화면 전체 노출
     */
    LaunchedEffect(
        rootHeight,
        sheetState.skipPartiallyExpanded,
        sheetState.partiallyExpandedFraction,
    ) {
        if (rootHeight <= 0f) {
            return@LaunchedEffect
        }

        val partiallyExpandedOffset =
            rootHeight *
                    (1f - sheetState.partiallyExpandedFraction)

        val anchors =
            DraggableAnchors {
                SheetValue.Hidden at rootHeight

                if (!sheetState.skipPartiallyExpanded) {
                    SheetValue.PartiallyExpanded at
                            partiallyExpandedOffset
                }

                SheetValue.Expanded at 0f
            }

        sheetState.anchoredDraggableState.updateAnchors(
            newAnchors = anchors,
        )

        /*
         * BottomSheet가 처음 생성되면 자동으로 표시한다.
         */
        if (
            sheetState.currentValue == SheetValue.Hidden &&
            sheetState.targetValue == SheetValue.Hidden
        ) {
            sheetState.show()
        }
    }

    /*
     * Sheet가 완전히 Hidden 상태가 되면
     * 호출부에서 BottomSheet Composable을 제거할 수 있도록 알린다.
     */
    LaunchedEffect(sheetState) {
        var hasBeenShown = false

        snapshotFlow {
            sheetState.currentValue to
                    sheetState.targetValue
        }.collect { (currentValue, targetValue) ->

            if (
                currentValue != SheetValue.Hidden ||
                targetValue != SheetValue.Hidden
            ) {
                hasBeenShown = true
            }

            if (
                hasBeenShown &&
                currentValue == SheetValue.Hidden &&
                targetValue == SheetValue.Hidden
            ) {
                hasBeenShown = false

                currentOnDismissRequest()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged {
                rootHeight =
                    it.height.toFloat()
            }
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Top +
                            WindowInsetsSides.Horizontal,
                ),
            ),
    ) {
        Surface(
            modifier = modifier
                .align(
                    Alignment.TopCenter,
                )
                .fillMaxWidth()
                .fillMaxHeight()
                .offsetForSheet(
                    sheetState = sheetState,
                    fallback = rootHeight,
                )
                .graphicsLayer {
                    alpha =
                        if (
                            sheetState
                                .anchoredDraggableState
                                .offset
                                .isNaN()
                        ) {
                            0f
                        } else {
                            1f
                        }
                },
            shape = shape,
            color = containerColor,
            contentColor = contentColor,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(
                        WindowInsets.navigationBars,
                    ),
            ) {
                /*
                 * Sheet 자체를 움직일 수 있는 영역.
                 *
                 * anchoredDraggable을 여기만 적용하므로
                 * Body 영역을 드래그해도 Sheet가 직접 움직이지 않는다.
                 */
                if (dragHandle != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (sheetGesturesEnabled) {
                                    Modifier.anchoredDraggable(
                                        state =
                                            sheetState
                                                .anchoredDraggableState,
                                        orientation =
                                            Orientation.Vertical,
                                    )
                                } else {
                                    Modifier
                                },
                            )
                            .padding(
                                top = 12.dp,
                                bottom = 16.dp,
                            ),
                        contentAlignment =
                            Alignment.Center,
                    ) {
                        dragHandle()
                    }
                }

                content()
            }
        }
    }
}

private fun Modifier.offsetForSheet(
    sheetState: SheetState,
    fallback: Float,
): Modifier =
    offset {
        val offset =
            sheetState
                .anchoredDraggableState
                .offset

        IntOffset(
            x = 0,
            y =
                if (offset.isNaN()) {
                    fallback.roundToInt()
                } else {
                    offset.roundToInt()
                },
        )
    }