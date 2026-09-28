package com.ssafy.motif.core.designsystem.component

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ssafy.motif.core.designsystem.glass.GlassBackdropState
import com.ssafy.motif.core.designsystem.glass.glassBackdrop
import com.ssafy.motif.core.designsystem.glass.rememberGlassBackdropState
import com.ssafy.motif.core.designsystem.icon.MotifIcons
import com.ssafy.motif.core.designsystem.theme.MotifTheme

/**
 * Glass 배경의 pill 형태 네비게이션 바.
 *
 * 실제 네비게이션 구성은 외부에서 [content]로 전달한다.
 * Main Navigation과 Motif Navigation 모두 동일한 컴포넌트를 사용한다.
 */
@Composable
fun GlassNavigationBar(
    backdropState: GlassBackdropState,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    GlassNavigationBarContainer(
        backdropState = backdropState,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(GlassNavigationBarDefaults.Height)
                .padding(
                    horizontal = GlassNavigationBarDefaults.HorizontalPadding,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            content()
        }
    }
}

/**
 * 일반 Painter 아이콘을 사용하는 Navigation Item.
 */
@Composable
fun RowScope.GlassNavigationBarItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: Painter,
    label: String,
    modifier: Modifier = Modifier,
    colors: GlassNavigationBarItemColors = GlassNavigationBarDefaults.itemColors(),
) {
    GlassNavigationBarItem(
        selected = selected,
        onClick = onClick,
        label = label,
        modifier = modifier,
        colors = colors,
        iconContent = {
            Icon(
                painter = icon,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
        },
    )
}

/**
 * 프로필 이미지 등 Custom Icon UI를 사용하는 Navigation Item.
 */
@Composable
fun RowScope.GlassNavigationBarItem(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    colors: GlassNavigationBarItemColors = GlassNavigationBarDefaults.itemColors(),
    iconContent: @Composable () -> Unit,
) {
    TabTransition(
        activeColor = colors.selectedContentColor,
        inactiveColor = colors.unselectedContentColor,
        selected = selected,
    ) {
        Column(
            modifier = modifier
                .weight(1f)
                .fillMaxHeight()
                .selectable(
                    selected = selected,
                    onClick = onClick,
                    role = Role.Tab,
                    interactionSource = null,
                    indication = null,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier.size(
                    GlassNavigationBarDefaults.IconSize,
                ),
                contentAlignment = Alignment.Center,
            ) {
                iconContent()
            }

            Spacer(
                modifier = Modifier.height(4.dp),
            )

            Text(
                text = label,
                style = MotifTheme.typography.captionM12,
                maxLines = 1,
            )
        }
    }
}

/**
 * Motif Navigation의 첫 번째 위치에서 사용하는 뒤로가기 버튼.
 *
 * 별도 leading slot 없이 GlassNavigationBar content 안에 직접 배치한다.
 */
@Composable
fun GlassNavigationBarBackButton(
    onClick: () -> Unit,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    ClickableSurface(
        onClick = onClick,
        modifier = modifier.size(
            GlassNavigationBarDefaults.BackButtonSize,
        ),
        shape = CircleShape,
        color = Color.Black.copy(
            alpha = 0.5f,
        ),
        contentColor = MotifTheme.colors.white,
        border = BorderStroke(
            width = 1.dp,
            color = Color.White.copy(
                alpha = 0.15f,
            ),
        ),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(
                    MotifIcons.BarArrowLeft,
                ),
                contentDescription = contentDescription,
                modifier = Modifier.size(
                    GlassNavigationBarDefaults.IconSize,
                ),
            )
        }
    }
}

@Composable
private fun GlassNavigationBarContainer(
    backdropState: GlassBackdropState,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        GlassSurface(
            backdropState = backdropState,
            modifier = modifier,
            shape = GlassNavigationBarDefaults.Shape,
            blurRadius = 8.dp,
            blurAlpha = 1f,
            tintColor = Color.Black.copy(alpha = 0.2f),
            content = content,
        )
    } else {
        Surface(
            modifier = modifier,
            shape = GlassNavigationBarDefaults.Shape,
            color = Color.Black.copy(
                alpha = 0.7f,
            ),
            contentColor = MotifTheme.colors.white,
            border = BorderStroke(
                width = 1.dp,
                color = Color.White.copy(
                    alpha = 0.15f,
                ),
            ),
            content = content,
        )
    }
}

object GlassNavigationBarDefaults {

    val Height: Dp = 60.dp

    val HorizontalPadding: Dp = 8.dp

    val IconSize: Dp = 24.dp

    val BackButtonSize: Dp = 48.dp

    val HorizontalMargin: Dp = 20.dp

    val BottomMargin: Dp = 16.dp

    val Shape: Shape = RoundedCornerShape(
        percent = 50,
    )

    @Composable
    fun itemColors(
        selectedContentColor: Color = MotifTheme.colors.white,
        unselectedContentColor: Color = MotifTheme.colors.gray400,
    ): GlassNavigationBarItemColors =
        GlassNavigationBarItemColors(
            selectedContentColor = selectedContentColor,
            unselectedContentColor = unselectedContentColor,
        )
}

@Immutable
class GlassNavigationBarItemColors(
    val selectedContentColor: Color,
    val unselectedContentColor: Color,
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GlassNavigationBarItemColors) return false

        if (selectedContentColor != other.selectedContentColor) return false
        if (unselectedContentColor != other.unselectedContentColor) return false

        return true
    }

    override fun hashCode(): Int {
        var result = selectedContentColor.hashCode()
        result = 31 * result + unselectedContentColor.hashCode()

        return result
    }
}

@Preview
@Composable
private fun GlassNavigationBarPreview() {
    MotifTheme {
        val backdropState = rememberGlassBackdropState()

        Box(
            modifier = Modifier
                .size(
                    width = 400.dp,
                    height = 200.dp,
                ),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .glassBackdrop(backdropState)
                    .background(
                        MotifTheme.colors.background,
                    ),
            )

            GlassNavigationBar(
                backdropState = backdropState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(20.dp),
            ) {
                GlassNavigationBarBackButton(
                    onClick = {},
                    contentDescription = "뒤로가기",
                )

                GlassNavigationBarItem(
                    selected = true,
                    onClick = {},
                    icon = painterResource(
                        MotifIcons.Motif,
                    ),
                    label = "모티프",
                )

                GlassNavigationBarItem(
                    selected = false,
                    onClick = {},
                    icon = painterResource(
                        MotifIcons.Bag,
                    ),
                    label = "SHOP",
                )

                GlassNavigationBarItem(
                    selected = false,
                    onClick = {},
                    icon = painterResource(
                        MotifIcons.MediaStrip,
                    ),
                    label = "보관함",
                )
            }
        }
    }
}