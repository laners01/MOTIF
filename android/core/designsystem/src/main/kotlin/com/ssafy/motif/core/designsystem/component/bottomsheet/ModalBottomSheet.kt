package com.ssafy.motif.core.designsystem.component.bottomsheet

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ssafy.motif.core.designsystem.component.Surface
import com.ssafy.motif.core.designsystem.theme.MotifTheme
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 화면 하단에서 표시되는 모달 형태의 Bottom Sheet.
 *
 * @param onDismissRequest Bottom Sheet가 닫힌 후 호출되는 콜백.
 * @param modifier Bottom Sheet 컨테이너에 적용할 Modifier.
 * @param sheetState Bottom Sheet의 상태와 애니메이션을 관리하는 객체.
 * @param shape Bottom Sheet 컨테이너의 Shape.
 * @param containerColor Bottom Sheet의 배경 색상.
 * @param contentColor Bottom Sheet 내부 콘텐츠의 기본 색상.
 * @param scrimColor Bottom Sheet 뒤쪽에 표시되는 Scrim 색상.
 * @param sheetGesturesEnabled Bottom Sheet 드래그 가능 여부.
 * @param dragHandle Bottom Sheet 상단에 표시되는 Drag Handle.
 * @param content Bottom Sheet 내부 콘텐츠.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
    shape: Shape = BottomSheetDefaults.ExpandedShape,
    containerColor: Color = MotifTheme.colors.background,
    contentColor: Color = MotifTheme.colors.typo,
    scrimColor: Color = BottomSheetDefaults.ScrimColor,
    sheetGesturesEnabled: Boolean = true,
    dragHandle: @Composable (() -> Unit)? = {
        BottomSheetDefaults.DragHandle()
    },
    content: @Composable ColumnScope.() -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()

    val currentOnDismissRequest by rememberUpdatedState(
        onDismissRequest,
    )

    var rootHeight by remember {
        mutableFloatStateOf(0f)
    }

    val nestedScrollConnection =
        remember(sheetState) {
            sheetNestedScrollConnection(
                sheetState = sheetState,
            )
        }

    /*
     * Sheet는 항상 화면 전체 높이를 가진다.
     *
     * Hidden
     *   -> 화면 아래
     *
     * PartiallyExpanded
     *   -> 화면의 60% 노출
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
         * BottomSheet가 처음 생성되면
         * 사용자의 드래그를 기다리지 않고 바로 표시한다.
         */
        if (
            sheetState.currentValue == SheetValue.Hidden &&
            sheetState.targetValue == SheetValue.Hidden
        ) {
            sheetState.show()
        }
    }

    /*
     * 드래그 등을 통해 Hidden까지 내려갔을 때
     * 실제 BottomSheet를 제거할 수 있도록 알린다.
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

    val dismissSheet: () -> Unit = {
        coroutineScope.launch {
            sheetState.hide()
        }
    }

    Dialog(
        onDismissRequest = dismissSheet,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged {
                    rootHeight = it.height.toFloat()
                },
        ) {
            BottomSheetScrim(
                color = scrimColor,
                visible = sheetState.isVisible,
                onDismissRequest = dismissSheet,
            )

            Surface(
                modifier = modifier
                    .align(Alignment.TopCenter)
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
                    }
                    .then(
                        if (sheetGesturesEnabled) {
                            Modifier
                                .nestedScroll(
                                    nestedScrollConnection,
                                )
                                .anchoredDraggable(
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
                    if (dragHandle != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    top = 12.dp,
                                    bottom = 16.dp,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            dragHandle()
                        }
                    }

                    content()
                }
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

@Composable
private fun BottomSheetScrim(
    color: Color,
    visible: Boolean,
    onDismissRequest: () -> Unit,
) {
    val alpha by animateFloatAsState(
        targetValue =
            if (visible) {
                1f
            } else {
                0f
            },
        animationSpec = tween(
            durationMillis = 200,
        ),
        label = "BottomSheetScrim",
    )

    if (color == Color.Unspecified) {
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                this.alpha = alpha
            }
            .background(color)
            .clickable(
                enabled = visible,
                interactionSource =
                    remember {
                        MutableInteractionSource()
                    },
                indication = null,
                onClick = onDismissRequest,
            ),
    )
}

private fun sheetNestedScrollConnection(
    sheetState: SheetState,
): NestedScrollConnection =
    object : NestedScrollConnection {

        override fun onPreScroll(
            available: Offset,
            source: NestedScrollSource,
        ): Offset {
            if (
                source != NestedScrollSource.UserInput ||
                available.y >= 0f
            ) {
                return Offset.Zero
            }

            /*
             * 위로 스크롤할 때 Sheet가 아직 Expanded가 아니라면
             * 내부 리스트보다 Sheet를 먼저 올린다.
             */
            val consumed =
                sheetState
                    .anchoredDraggableState
                    .dispatchRawDelta(
                        available.y,
                    )

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
            if (source != NestedScrollSource.UserInput) {
                return Offset.Zero
            }

            /*
             * LazyColumn이 더 이상 스크롤되지 않을 때
             * 남은 움직임을 Sheet가 소비한다.
             */
            val consumedBySheet =
                sheetState
                    .anchoredDraggableState
                    .dispatchRawDelta(
                        available.y,
                    )

            return Offset(
                x = 0f,
                y = consumedBySheet,
            )
        }

        override suspend fun onPreFling(
            available: Velocity,
        ): Velocity {
            val state =
                sheetState.anchoredDraggableState

            val currentOffset =
                state.offset

            val expandedPosition =
                state.anchors
                    .positionOf(
                        SheetValue.Expanded,
                    )

            return if (
                available.y < 0f &&
                !currentOffset.isNaN() &&
                !expandedPosition.isNaN() &&
                currentOffset > expandedPosition
            ) {
                sheetState.settle(
                    velocity = available.y,
                )

                available
            } else {
                Velocity.Zero
            }
        }

        override suspend fun onPostFling(
            consumed: Velocity,
            available: Velocity,
        ): Velocity {
            if (available.y != 0f) {
                sheetState.settle(
                    velocity = available.y,
                )
            }

            return available
        }
    }