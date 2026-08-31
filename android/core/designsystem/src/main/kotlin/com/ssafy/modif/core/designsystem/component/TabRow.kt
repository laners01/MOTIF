package com.ssafy.modif.core.designsystem.component

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.debugInspectorInfo
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastFold
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastForEachIndexed
import androidx.compose.ui.util.fastMap
import com.ssafy.modif.core.designsystem.component.TabRowDefaults.tabIndicatorOffset
import com.ssafy.modif.core.designsystem.theme.ModifTheme

@Composable
fun TabRow(
    selectedTabIndex: Int,
    modifier: Modifier = Modifier,
    containerColor: Color = Color.Transparent,
    contentColor: Color = ModifTheme.colors.gray700,
    indicator: @Composable (tabPositions: List<TabPosition>) -> Unit =
        @Composable { tabPositions ->
            if (selectedTabIndex < tabPositions.size) {
                TabRowDefaults.Indicator(
                    Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex])
                )
            }
        },
    divider: @Composable () -> Unit = @Composable { HorizontalDivider() },
    tabs: @Composable () -> Unit
) {
    TabRowWithSubComposeImpl(modifier, containerColor, contentColor, indicator, divider, tabs)
}

@Composable
private fun TabRowWithSubComposeImpl(
    modifier: Modifier,
    containerColor: Color,
    contentColor: Color,
    indicator: @Composable (tabPositions: List<TabPosition>) -> Unit,
    divider: @Composable () -> Unit,
    tabs: @Composable () -> Unit
) {
    Surface(
        modifier = modifier.selectableGroup(),
        color = containerColor,
        contentColor = contentColor
    ) {
        SubcomposeLayout(Modifier.fillMaxWidth()) { constraints ->
            val tabRowWidth = constraints.maxWidth
            val tabMeasurables = subcompose(TabSlots.Tabs, tabs)
            val tabCount = tabMeasurables.size
            var tabWidth = 0
            if (tabCount > 0) {
                tabWidth = (tabRowWidth / tabCount)
            }
            val tabRowHeight =
                tabMeasurables.fastFold(initial = 0) { max, curr ->
                    maxOf(curr.maxIntrinsicHeight(tabWidth), max)
                }

            val tabPlaceable =
                tabMeasurables.fastMap {
                    it.measure(
                        constraints.copy(
                            minWidth = tabWidth,
                            maxWidth = tabWidth,
                            minHeight = tabRowHeight,
                            maxHeight = tabRowHeight,
                        )
                    )
                }

            val tabPositions =
                List(tabCount) { index ->
                    var contentWidth =
                        minOf(tabMeasurables[index].maxIntrinsicWidth(tabRowHeight), tabWidth)
                            .toDp()
                    contentWidth -= HorizontalTextPadding * 2
                    val indicatorWidth = maxOf(contentWidth, 24.dp)
                    TabPosition(tabWidth.toDp() * index, tabWidth.toDp(), indicatorWidth)
                }

            layout(tabRowWidth, tabRowHeight) {
                tabPlaceable.fastForEachIndexed { index, placeable ->
                    placeable.placeRelative(index * tabWidth, 0)
                }

                subcompose(TabSlots.Divider, divider).fastForEach {
                    val placeable = it.measure(constraints.copy(minHeight = 0))
                    placeable.placeRelative(0, tabRowHeight - placeable.height)
                }

                subcompose(TabSlots.Indicator) { indicator(tabPositions) }
                    .fastForEach {
                        it.measure(Constraints.fixed(tabRowWidth, tabRowHeight)).placeRelative(0, 0)
                    }
            }
        }
    }
}

internal val HorizontalTextPadding = 16.dp

private enum class TabSlots {
    Tabs,
    Divider,
    Indicator
}


// Todo: color 변경
object TabRowDefaults {
    @Composable
    fun Indicator(
        modifier: Modifier = Modifier,
        color: Color = ModifTheme.colors.black,
        shape: RoundedCornerShape = RoundedCornerShape(2.dp),
        height: Dp = 4.dp
    ) {
        Box(
            modifier
                .fillMaxWidth()
                .height(height)
                .background(color = color, shape = shape)
        )
    }

    fun Modifier.tabIndicatorOffset(currentTabPosition: TabPosition): Modifier =
        composed(
            inspectorInfo =
                debugInspectorInfo {
                    name = "tabIndicatorOffset"
                    value = currentTabPosition
                }
        ) {
            val currentTabWidth by
            animateDpAsState(
                targetValue = currentTabPosition.width,
                animationSpec = TabRowIndicatorSpec
            )
            val indicatorOffset by
            animateDpAsState(
                targetValue = currentTabPosition.left,
                animationSpec = TabRowIndicatorSpec
            )
            fillMaxWidth()
                .wrapContentSize(Alignment.BottomStart)
                .offset { IntOffset(x = indicatorOffset.roundToPx(), y = 0) }
                .width(currentTabWidth)
        }
}

@Immutable
class TabPosition internal constructor(val left: Dp, val width: Dp, val contentWidth: Dp) {

    val right: Dp
        get() = left + width

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TabPosition) return false

        if (left != other.left) return false
        if (width != other.width) return false
        if (contentWidth != other.contentWidth) return false

        return true
    }

    override fun hashCode(): Int {
        var result = left.hashCode()
        result = 31 * result + width.hashCode()
        result = 31 * result + contentWidth.hashCode()
        return result
    }

    override fun toString(): String {
        return "TabPosition(left=$left, right=$right, width=$width, contentWidth=$contentWidth)"
    }
}

private val TabRowIndicatorSpec: AnimationSpec<Dp> =
    tween(durationMillis = 250, easing = FastOutSlowInEasing)