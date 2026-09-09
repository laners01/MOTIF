package com.ssafy.motif.core.designsystem.glass

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
internal fun Modifier.glassEffect(
    backdropState: GlassBackdropState,
    blurRadius: Dp = 16.dp,
    blurAlpha: Float = 0.20f,
    tintColor: Color = Color.White.copy(alpha = 0.2f),
    highlightAlpha: Float = 0.12f,
): Modifier {
    val density = LocalDensity.current

    val blurLayer = rememberGraphicsLayer()

    var glassPositionInRoot by remember {
        mutableStateOf(Offset.Zero)
    }

    val blurRadiusPx = with(density) {
        blurRadius.toPx()
    }

    val blurEffect = remember(blurRadiusPx) {
        BlurEffect(
            radiusX = blurRadiusPx,
            radiusY = blurRadiusPx,
            edgeTreatment = TileMode.Clamp,
        )
    }

    return this
        .onGloballyPositioned { coordinates ->
            glassPositionInRoot = coordinates.positionInRoot()
        }
        .drawWithContent {
            if (
                size.width <= 0f ||
                size.height <= 0f
            ) {
                drawContent()
                return@drawWithContent
            }

            val backdropOffset =
                backdropState.positionInRoot -
                        glassPositionInRoot

            /*
             * 중요:
             *
             * Glass 영역 전체를 blurred backdrop으로 대체하지 않는다.
             *
             * 실제 배경은 이미 Glass 뒤에 그려져 있으므로,
             * blur된 복사본만 낮은 alpha로 위에 겹친다.
             */
            blurLayer.renderEffect = blurEffect

            blurLayer.alpha =
                blurAlpha.coerceIn(
                    minimumValue = 0f,
                    maximumValue = 1f,
                )

            blurLayer.record {
                translate(
                    left = backdropOffset.x,
                    top = backdropOffset.y,
                ) {
                    drawLayer(
                        backdropState.graphicsLayer,
                    )
                }
            }

            drawLayer(blurLayer)

            /*
             * 아주 약한 White Tint.
             *
             * 20%처럼 강하게 칠하지 않고
             * Glass 표면이 있다는 느낌만 준다.
             */
            drawRect(
                color = tintColor,
            )

            /*
             * 좌상단 → 우하단 방향의 아주 약한 광택.
             *
             * 단순한 반투명 Box보다
             * Glass처럼 보이게 만드는 부분.
             */
            drawRect(
                brush =
                    Brush.linearGradient(
                        colors =
                            listOf(
                                Color.White.copy(
                                    alpha = highlightAlpha,
                                ),
                                Color.White.copy(
                                    alpha = highlightAlpha * 0.25f,
                                ),
                                Color.Transparent,
                            ),
                        start = Offset.Zero,
                        end =
                            Offset(
                                x = size.width,
                                y = size.height,
                            ),
                    ),
            )

            /*
             * Text / Icon / 기타 실제 Content
             */
            drawContent()
        }
}