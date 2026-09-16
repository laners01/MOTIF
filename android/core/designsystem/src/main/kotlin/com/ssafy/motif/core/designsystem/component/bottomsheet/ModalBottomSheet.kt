package com.ssafy.motif.core.designsystem.component.bottomsheet

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import com.ssafy.motif.core.designsystem.component.Surface
import com.ssafy.motif.core.designsystem.theme.MotifTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
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
        DefaultDragHandle()
    },
    content: @Composable ColumnScope.() -> Unit,
) {
    val coroutineScope =
        rememberCoroutineScope()

    val currentOnDismissRequest by
    rememberUpdatedState(
        onDismissRequest,
    )

    var rootHeight by remember {
        mutableFloatStateOf(0f)
    }

    val nestedScrollConnection =
        remember(
            sheetState,
            coroutineScope,
        ) {
            sheetContentNestedScrollConnection(
                sheetState = sheetState,
                coroutineScope = coroutineScope,
            )
        }

    /*
     * Sheet Anchor
     *
     * Expanded
     *   -> y = 0
     *
     * PartiallyExpanded
     *   -> 화면의 일부만 노출
     *
     * Hidden
     *   -> y = rootHeight
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
                    (
                            1f -
                                    sheetState.partiallyExpandedFraction
                            )

        val anchors =
            DraggableAnchors {
                SheetValue.Hidden at rootHeight

                if (!sheetState.skipPartiallyExpanded) {
                    SheetValue.PartiallyExpanded at
                            partiallyExpandedOffset
                }

                SheetValue.Expanded at 0f
            }

        sheetState
            .anchoredDraggableState
            .updateAnchors(
                newAnchors = anchors,
            )

        /*
         * 최초 진입 시 Partial 또는 Expanded 상태로 표시.
         */
        if (
            sheetState.currentValue ==
            SheetValue.Hidden &&
            sheetState.targetValue ==
            SheetValue.Hidden
        ) {
            sheetState.show()
        }
    }

    /*
     * Hidden 애니메이션이 완전히 끝난 뒤
     * 호출부의 BottomSheet를 제거한다.
     */
    LaunchedEffect(
        sheetState,
    ) {
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

    val dismissSheet: () -> Unit = {
        coroutineScope.launch {
            sheetState.hide()
        }
    }

    BackHandler(
        enabled = sheetState.isVisible,
    ) {
        dismissSheet()
    }

    /*
     * 별도 Dialog를 사용하지 않는다.
     *
     * 따라서 Sheet 위쪽 영역에서는
     * 뒤쪽 화면과 그대로 상호작용할 수 있다.
     */
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { size ->
                rootHeight =
                    size.height.toFloat()
            },
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth()

                /*
                 * 핵심:
                 *
                 * 화면 전체 높이를 가진 Surface를
                 * 단순 offset 하는 것이 아니라,
                 *
                 * 실제 보이는 높이:
                 *
                 * rootHeight - sheetOffset
                 *
                 * 만큼만 측정한다.
                 */
                .sheetVisibleHeight(
                    sheetState = sheetState,
                    fallback = rootHeight,
                )

                /*
                 * Sheet의 실제 top 위치로 이동.
                 */
                .sheetOffset(
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
                    )
                    .then(
                        if (sheetGesturesEnabled) {
                            Modifier.nestedScroll(
                                nestedScrollConnection,
                            )
                        } else {
                            Modifier
                        },
                    ),
            ) {
                /*
                 * Header 영역에서만
                 * Sheet를 직접 드래그할 수 있다.
                 */
                if (dragHandle != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
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
                            ),
                    ) {
                        dragHandle()
                    }
                }

                /*
                 * Body 영역.
                 *
                 * LazyColumn 등의 스크롤 영역은
                 * 실제 Sheet 높이만큼만 측정된다.
                 */
                content()
            }
        }
    }
}

@Composable
private fun DefaultDragHandle() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                top = 12.dp,
                bottom = 16.dp,
            ),
        contentAlignment = Alignment.Center,
    ) {
        BottomSheetDefaults.DragHandle()
    }
}

/**
 * 현재 Sheet가 실제 화면에서 차지하는 높이를 계산한다.
 *
 * 예)
 *
 * rootHeight = 1000
 * offset = 400
 *
 * sheetHeight = 600
 *
 * 기존처럼 1000 높이의 Sheet를 400 아래로 이동하는 것이 아니라,
 * 실제 Sheet 자체를 600 높이로 측정한다.
 */
private fun Modifier.sheetVisibleHeight(
    sheetState: SheetState,
    fallback: Float,
): Modifier =
    layout {
            measurable,
            constraints,
        ->

        val rootHeight =
            constraints.maxHeight

        val rawOffset =
            sheetState
                .anchoredDraggableState
                .offset

        val sheetTop =
            if (rawOffset.isNaN()) {
                fallback.roundToInt()
            } else {
                rawOffset.roundToInt()
            }.coerceIn(
                minimumValue = 0,
                maximumValue = rootHeight,
            )

        val sheetHeight =
            (
                    rootHeight -
                            sheetTop
                    )
                .coerceAtLeast(0)

        val placeable =
            measurable.measure(
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

/**
 * Sheet의 top 위치를 실제 anchor offset과 동일하게 이동한다.
 */
private fun Modifier.sheetOffset(
    sheetState: SheetState,
    fallback: Float,
): Modifier =
    this.then(
        Modifier.layout {
                measurable,
                constraints,
            ->

            val placeable =
                measurable.measure(
                    constraints,
                )

            val rawOffset =
                sheetState
                    .anchoredDraggableState
                    .offset

            val y =
                if (rawOffset.isNaN()) {
                    fallback.roundToInt()
                } else {
                    rawOffset.roundToInt()
                }

            layout(
                width = placeable.width,
                height = placeable.height,
            ) {
                placeable.placeRelative(
                    x = 0,
                    y = y,
                )
            }
        },
    )

/**
 * Body 스크롤 처리.
 *
 * LazyColumn 등이 먼저 스크롤을 소비한다.
 *
 * Body가 최상단에 도달해 더 이상 아래 방향 스크롤을
 * 소비하지 못하는 경우에만 Sheet 상태를 한 단계 내린다.
 *
 * Expanded
 *   -> PartiallyExpanded
 *
 * PartiallyExpanded
 *   -> Hidden
 */
private fun sheetContentNestedScrollConnection(
    sheetState: SheetState,
    coroutineScope: CoroutineScope,
): NestedScrollConnection =
    object : NestedScrollConnection {

        private var isCollapsing =
            false

        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource,
        ): Offset {
            if (
                source != NestedScrollSource.UserInput ||
                available.y <= 0f
            ) {
                return Offset.Zero
            }

            val currentValue =
                sheetState.currentValue

            if (
                currentValue ==
                SheetValue.Hidden
            ) {
                return Offset.Zero
            }

            if (isCollapsing) {
                return Offset(
                    x = 0f,
                    y = available.y,
                )
            }

            isCollapsing = true

            coroutineScope.launch {
                try {
                    when (currentValue) {
                        SheetValue.Expanded -> {
                            if (
                                sheetState
                                    .skipPartiallyExpanded
                            ) {
                                sheetState.hide()
                            } else {
                                sheetState.partialExpand()
                            }
                        }

                        SheetValue.PartiallyExpanded -> {
                            sheetState.hide()
                        }

                        SheetValue.Hidden -> Unit
                    }
                } finally {
                    isCollapsing = false
                }
            }

            /*
             * LazyColumn이 소비하지 못한 pull-down을
             * 여기서 소비해서 overscroll과 중복되지 않게 한다.
             */
            return Offset(
                x = 0f,
                y = available.y,
            )
        }
    }