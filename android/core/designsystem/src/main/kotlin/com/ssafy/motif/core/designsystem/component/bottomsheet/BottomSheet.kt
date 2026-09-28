package com.ssafy.motif.core.designsystem.component.bottomsheet

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.ssafy.motif.core.designsystem.component.Surface
import com.ssafy.motif.core.designsystem.component.Text
import com.ssafy.motif.core.designsystem.theme.MotifTheme
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun BottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberBottomSheetState(),
    shape: Shape = BottomSheetDefaults.ExpandedShape,
    containerColor: Color = MotifTheme.colors.background,
    contentColor: Color = MotifTheme.colors.typo,
    sheetGesturesEnabled: Boolean = true,
    dragHandle: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    val currentOnDismissRequest by rememberUpdatedState(onDismissRequest)

    var rootHeight by remember {
        mutableIntStateOf(0)
    }

    val nestedScrollConnection = remember(sheetState) {
        sheetContentNestedScrollConnection(
            sheetState = sheetState,
        )
    }

    LaunchedEffect(
        rootHeight,
        sheetState.skipPartiallyExpanded,
        sheetState.partiallyExpandedFraction,
    ) {
        if (rootHeight <= 0) {
            return@LaunchedEffect
        }

        sheetState.updateSheetAnchors(
            rootHeight = rootHeight,
        )

        if (
            sheetState.currentValue == SheetValue.Hidden &&
            sheetState.targetValue == SheetValue.Hidden
        ) {
            sheetState.show()
        }
    }

    LaunchedEffect(sheetState) {
        var hasBeenShown = false

        snapshotFlow {
            sheetState.currentValue to sheetState.targetValue
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

    BackHandler(
        enabled = sheetState.isVisible,
    ) {
        coroutineScope.launch {
            sheetState.hide()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { size ->
                rootHeight = size.height
            },
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .sheetVisibleHeight(
                    sheetState = sheetState,
                )
                .offset {
                    IntOffset(
                        x = 0,
                        y = sheetState
                            .offsetOrNull()
                            ?.roundToInt()
                            ?: rootHeight,
                    )
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
                if (dragHandle != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (sheetGesturesEnabled) {
                                    Modifier.anchoredDraggable(
                                        state = sheetState.anchoredDraggableState,
                                        orientation = Orientation.Vertical,
                                    )
                                } else {
                                    Modifier
                                },
                            ),
                    ) {
                        dragHandle()
                    }
                }

                content()
            }
        }
    }
}

@Composable
fun ModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberBottomSheetState(),
    shape: Shape = BottomSheetDefaults.ExpandedShape,
    containerColor: Color = MotifTheme.colors.background,
    contentColor: Color = MotifTheme.colors.typo,
    scrimColor: Color = BottomSheetDefaults.ScrimColor,
    sheetGesturesEnabled: Boolean = true,
    dragHandle: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    val interactionSource = remember {
        MutableInteractionSource()
    }

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = sheetState.scrimProgress()
                }
                .background(
                    color = scrimColor,
                ),
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .scrimClickArea(
                    sheetState = sheetState,
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                ) {
                    coroutineScope.launch {
                        sheetState.hide()
                    }
                },
        )

        BottomSheet(
            onDismissRequest = onDismissRequest,
            modifier = modifier,
            sheetState = sheetState,
            shape = shape,
            containerColor = containerColor,
            contentColor = contentColor,
            sheetGesturesEnabled = sheetGesturesEnabled,
            dragHandle = dragHandle,
            content = content,
        )
    }
}

@Preview(
    name = "BottomSheet",
    showBackground = true,
)
@Composable
private fun BottomSheetPreview() {
    MotifTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.DarkGray),
        ) {
            Text(
                text = "Background Content",
                color = MotifTheme.colors.typo,
                style = MotifTheme.typography.titleSB18,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 120.dp),
            )

            BottomSheet(
                onDismissRequest = {},
                sheetState = rememberBottomSheetState(
                    skipPartiallyExpanded = false,
                    partiallyExpandedFraction = 0.6f,
                ),
                dragHandle = {
                    BottomSheetDefaults.DragHandle()
                },
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = 20.dp,
                            vertical = 16.dp,
                        ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "Bottom Sheet",
                        color = MotifTheme.colors.typo,
                        style = MotifTheme.typography.titleSB18,
                    )

                    Text(
                        text = "Bottom Sheet 콘텐츠를 확인하기 위한 미리보기입니다.",
                        color = MotifTheme.colors.typo,
                        style = MotifTheme.typography.bodySB16,
                    )
                }
            }
        }
    }
}

@Preview(
    name = "ModalBottomSheet",
    showBackground = true,
)
@Composable
private fun ModalBottomSheetPreview() {
    MotifTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.DarkGray),
        ) {
            Text(
                text = "Modal Background Content",
                color = MotifTheme.colors.typo,
                style = MotifTheme.typography.titleSB18,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 120.dp),
            )

            ModalBottomSheet(
                onDismissRequest = {},
                sheetState = rememberBottomSheetState(
                    skipPartiallyExpanded = false,
                    partiallyExpandedFraction = 0.6f,
                ),
                dragHandle = {
                    BottomSheetDefaults.DragHandle()
                },
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = 20.dp,
                            vertical = 16.dp,
                        ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "Modal Bottom Sheet",
                        color = MotifTheme.colors.typo,
                        style = MotifTheme.typography.titleSB18,
                    )

                    Text(
                        text = "Modal Bottom Sheet 콘텐츠를 확인하기 위한 미리보기입니다.",
                        color = MotifTheme.colors.typo,
                        style = MotifTheme.typography.bodySB16,
                    )
                }
            }
        }
    }
}