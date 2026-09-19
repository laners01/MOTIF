package com.ssafy.motif.core.designsystem.component

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ssafy.motif.core.designsystem.R
import com.ssafy.motif.core.designsystem.glass.GlassBackdropState
import com.ssafy.motif.core.designsystem.glass.glassBackdrop
import com.ssafy.motif.core.designsystem.glass.rememberGlassBackdropState
import com.ssafy.motif.core.designsystem.icon.MotifIcons
import com.ssafy.motif.core.designsystem.theme.MotifTheme

/**
 * Glass 배경의 pill 형태 네비게이션 바.
 *
 * 바 뒤에 그려진 콘텐츠를 blur해서 보여주므로, [backdropState]가 걸린 콘텐츠 영역이
 * 바 아래까지 채워져 있어야 효과가 나타난다. 바깥 여백(화면 좌우, 하단 인셋)은 [modifier]로 지정한다.
 *
 * @param leading 아이템 왼쪽에 고정 크기로 배치되는 슬롯. 서브 네비게이션의 뒤로가기 등.
 * @param content [GlassNavigationBarItem]들. 남은 폭을 균등 분할한다.
 */
@Composable
fun GlassNavigationBar(
    backdropState: GlassBackdropState,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
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
                .padding(horizontal = GlassNavigationBarDefaults.HorizontalPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leading?.invoke()
            content()
        }
    }
}

/**
 * [GlassNavigationBar] 안에 놓이는 아이콘 + 라벨 아이템.
 * 선택 상태에 따라 콘텐츠 색이 [colors] 사이에서 애니메이션된다.
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
            Icon(
                painter = icon,
                // 라벨이 이미 읽히므로 아이콘은 접근성 트리에서 생략한다.
                contentDescription = null,
                modifier = Modifier.size(GlassNavigationBarDefaults.IconSize),
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = label,
                style = MotifTheme.typography.captionM12,
                maxLines = 1,
            )
        }
    }
}

/**
 * [GlassNavigationBar]의 `leading` 슬롯에 놓이는 원형 뒤로가기 버튼.
 */
@Composable
fun GlassNavigationBarBackButton(
    onClick: () -> Unit,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    ClickableSurface(
        onClick = onClick,
        modifier = modifier.size(GlassNavigationBarDefaults.BackButtonSize),
        shape = CircleShape,
        color = Color.White.copy(alpha = 0.08f),
        contentColor = MotifTheme.colors.white,
        border = BorderStroke(
            width = 1.dp,
            color = Color.White.copy(alpha = 0.15f),
        ),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(MotifIcons.BarArrowLeft),
                contentDescription = contentDescription,
                modifier = Modifier.size(GlassNavigationBarDefaults.IconSize),
            )
        }
    }
}

/**
 * Glass 효과는 API 33 이상에서만 지원되므로, 그 이하에서는 불투명도 높은 단색 pill로 대체한다.
 */
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
            blurRadius = 16.dp,
            blurAlpha = 1f,
            tintColor = Color.Black.copy(alpha = 0.35f),
            content = content,
        )
    } else {
        Surface(
            modifier = modifier,
            shape = GlassNavigationBarDefaults.Shape,
            color = Color.Black.copy(alpha = 0.7f),
            contentColor = MotifTheme.colors.white,
            border = BorderStroke(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.15f),
            ),
            content = content,
        )
    }
}

object GlassNavigationBarDefaults {
    val Height: Dp = 72.dp
    val HorizontalPadding: Dp = 8.dp
    val IconSize: Dp = 24.dp
    val BackButtonSize: Dp = 56.dp
    val Shape: Shape = RoundedCornerShape(percent = 50)

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
        if (other == null || other !is GlassNavigationBarItemColors) return false

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

/**
 * Glass는 뒤에 그려진 콘텐츠가 있어야 보이므로, backdrop이 걸린 이미지 배경 위에 그린다.
 */
@Preview
@Composable
private fun GlassNavigationBarPreview() {
    MotifTheme {
        val backdropState = rememberGlassBackdropState()

        Box(
            modifier = Modifier.size(width = 400.dp, height = 200.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .glassBackdrop(backdropState),
            ) {
                Image(
                    painter = painterResource(R.drawable.img_challenge),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            GlassNavigationBar(
                backdropState = backdropState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(20.dp),
                leading = {
                    GlassNavigationBarBackButton(
                        onClick = {},
                        contentDescription = "뒤로가기",
                    )
                },
            ) {
                GlassNavigationBarItem(
                    selected = false,
                    onClick = {},
                    icon = painterResource(MotifIcons.Motif),
                    label = "모티프",
                )
                GlassNavigationBarItem(
                    selected = false,
                    onClick = {},
                    icon = painterResource(MotifIcons.Bag),
                    label = "Shop",
                )
                GlassNavigationBarItem(
                    selected = true,
                    onClick = {},
                    icon = painterResource(MotifIcons.MediaStrip),
                    label = "보관함",
                )
            }
        }
    }
}
