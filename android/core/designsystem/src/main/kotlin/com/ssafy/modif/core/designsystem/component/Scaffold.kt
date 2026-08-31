package com.ssafy.modif.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.MutableWindowInsets
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.onConsumedWindowInsetsChanged
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import com.ssafy.modif.core.designsystem.theme.LocalModifContentColor
import com.ssafy.modif.core.designsystem.theme.ModifTheme

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Scaffold(
    modifier: Modifier = Modifier,
    containerColor: Color = ModifTheme.colors.gray50,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    contentColor: Color = LocalModifContentColor.current,
    contentWindowInsets: WindowInsets = WindowInsets.statusBars,
    content: @Composable (PaddingValues) -> Unit
) {
    val safeInsets = remember(contentWindowInsets) { MutableWindowInsets(contentWindowInsets) }
    Surface(
        modifier =
            modifier.onConsumedWindowInsetsChanged { consumedWindowInsets ->
                // 현재까지 소비된(WindowInsets 적용이 완료된) 인셋을 제외한 나머지를, 사용자가 제공한 contentWindowInsets에서 계산합니다.
                // WindowInsets를 사용할 때 중복 적용을 피하기 위한 처리를 의미합니다.
                safeInsets.insets = contentWindowInsets.exclude(consumedWindowInsets)
            },
        color = containerColor,
        contentColor = contentColor
    ) {
        ScaffoldLayout(
            topBar = topBar,
            bottomBar = bottomBar,
            content = content,
            snackbar = snackbarHost,
            contentWindowInsets = safeInsets,
        )
    }
}

@Composable
private fun ScaffoldLayout(
    topBar: @Composable () -> Unit,
    content: @Composable (PaddingValues) -> Unit,
    snackbar: @Composable () -> Unit,
    contentWindowInsets: WindowInsets,
    bottomBar: @Composable () -> Unit
) {
    // content padding을 위한 내부 값(backing value)을 생성합니다.
    // 이 값들은 측정 과정 중에 업데이트되지만, 본문 콘텐츠를 subcompose하기 전에 설정됩니다.
    // 단일 PaddingValues를 remember하고 업데이트함으로써 값이 변경되어도 recomposition을 피할 수 있습니다.

    val contentPadding = remember {
        object : PaddingValues {
            var paddingHolder by mutableStateOf(PaddingValues(0.dp))

            override fun calculateLeftPadding(layoutDirection: LayoutDirection): Dp =
                paddingHolder.calculateLeftPadding(layoutDirection)

            override fun calculateTopPadding(): Dp = paddingHolder.calculateTopPadding()

            override fun calculateRightPadding(layoutDirection: LayoutDirection): Dp =
                paddingHolder.calculateRightPadding(layoutDirection)

            override fun calculateBottomPadding(): Dp = paddingHolder.calculateBottomPadding()
        }
    }

    SubcomposeLayout { constraints ->
        val layoutWidth = constraints.maxWidth
        val layoutHeight = constraints.maxHeight

        val looseConstraints = constraints.copy(minWidth = 0, minHeight = 0)

        // Snackbar와 FAB에는 하단과 좌우 방향의 패딩만 고려하며, 상단 패딩은 무시합니다.
        val leftInset = contentWindowInsets.getLeft(this@SubcomposeLayout, layoutDirection)
        val rightInset = contentWindowInsets.getRight(this@SubcomposeLayout, layoutDirection)
        val bottomInset = contentWindowInsets.getBottom(this@SubcomposeLayout)

        val topBarPlaceable =
            subcompose(ScaffoldLayoutContent.TopBar) { Box { topBar() } }
                .first()
                .measure(looseConstraints)

        val snackbarPlaceable =
            subcompose(ScaffoldLayoutContent.Snackbar) { Box { snackbar() } }
                .first()
                .measure(looseConstraints.offset(-leftInset - rightInset, -bottomInset))

        val bottomBarPlaceable =
            subcompose(ScaffoldLayoutContent.BottomBar) { Box { bottomBar() } }
                .first()
                .measure(looseConstraints)

        val isBottomBarEmpty = bottomBarPlaceable.width == 0 && bottomBarPlaceable.height == 0

        val snackbarHeight = snackbarPlaceable.height
        val snackbarOffsetFromBottom =
            if (snackbarHeight != 0) {
                snackbarHeight +
                        (bottomBarPlaceable.height.takeIf { !isBottomBarEmpty }
                            ?: contentWindowInsets.getBottom(this@SubcomposeLayout))
            } else {
                0
            }
        // 본문 콘텐츠를 실제로 구성(subcompose)하기 전에 padding 값을 내부 상태에 반영합니다.
        val insets = contentWindowInsets.asPaddingValues(this)
        contentPadding.paddingHolder =
            PaddingValues(
                top =
                    if (topBarPlaceable.width == 0 && topBarPlaceable.height == 0) {
                        insets.calculateTopPadding()
                    } else {
                        topBarPlaceable.height.toDp()
                    },
                bottom =
                    if (isBottomBarEmpty) {
                        insets.calculateBottomPadding()
                    } else {
                        bottomBarPlaceable.height.toDp()
                    },
                start = insets.calculateStartPadding(layoutDirection),
                end = insets.calculateEndPadding(layoutDirection)
            )

        val bodyContentPlaceable =
            subcompose(ScaffoldLayoutContent.MainContent) { Box { content(contentPadding) } }
                .first()
                .measure(looseConstraints)

        layout(layoutWidth, layoutHeight) {
            // 각 요소(placeable)의 기본 elevation에 맞춰 그리기 순서를 제어하기 위해 배치합니다.
            bodyContentPlaceable.place(0, 0)
            topBarPlaceable.place(0, 0)
            snackbarPlaceable.place(
                (layoutWidth - snackbarPlaceable.width) / 2 +
                        contentWindowInsets.getLeft(this@SubcomposeLayout, layoutDirection),
                layoutHeight - snackbarOffsetFromBottom
            )
            // BottomBar는 항상 화면 레이아웃의 가장 아래쪽에 배치됩니다.
            bottomBarPlaceable.place(0, layoutHeight - (bottomBarPlaceable.height))
        }
    }
}

private enum class ScaffoldLayoutContent {
    TopBar,
    MainContent,
    Snackbar,
    BottomBar
}