package com.ssafy.motif.core.designsystem.component

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.Indication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ssafy.motif.core.designsystem.R
import com.ssafy.motif.core.designsystem.glass.GlassBackdropState
import com.ssafy.motif.core.designsystem.glass.glassBackdrop
import com.ssafy.motif.core.designsystem.glass.glassEffect
import com.ssafy.motif.core.designsystem.glass.rememberGlassBackdropState
import com.ssafy.motif.core.designsystem.theme.LocalMotifContentColor

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
fun GlassSurface(
    backdropState: GlassBackdropState,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    contentColor: Color = Color.White,
    blurRadius: Dp = 12.dp,
    blurAlpha: Float = 1f,
    tintColor: Color = Color.White.copy(alpha = 0.2f),
    borderWidth: Dp = 1.dp,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable () -> Unit,
) {
    val borderBrush =
        remember {
            Brush.linearGradient(
                colors =
                    listOf(
                        Color.White.copy(alpha = 0.45f),
                        Color.White.copy(alpha = 0.18f),
                        Color.White.copy(alpha = 0.06f),
                    ),
            )
        }

    CompositionLocalProvider(
        LocalMotifContentColor provides contentColor,
    ) {
        Box(
            modifier =
                modifier
                    .clip(shape)
                    .glassEffect(
                        backdropState = backdropState,
                        blurRadius = blurRadius,
                        blurAlpha = blurAlpha,
                        tintColor = tintColor,
                    )
                    .border(
                        border =
                            BorderStroke(
                                width = borderWidth,
                                brush = borderBrush,
                            ),
                        shape = shape,
                    )
                    .padding(contentPadding),
            propagateMinConstraints = true,
        ) {
            content()
        }
    }
}
@Composable
@NonRestartableComposable
fun Surface(
    modifier: Modifier = Modifier,
    shape: Shape = RectangleShape,
    color: Color = Color.Unspecified,
    contentColor: Color = Color.Unspecified,
    shadowElevation: Dp = 0.dp,
    border: BorderStroke? = null,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalMotifContentColor provides contentColor,
    ) {
        Box(
            modifier =
                modifier
                    .surface(
                        shape = shape,
                        backgroundColor = color,
                        border = border,
                        shadowElevation = with(LocalDensity.current) { shadowElevation.toPx() }
                    )
                    .padding(contentPadding),
            propagateMinConstraints = true,
        ) {
            content()
        }
    }
}

@Composable
@NonRestartableComposable
fun ClickableSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RectangleShape,
    color: Color = Color.Unspecified,
    contentColor: Color = Color.Unspecified,
    shadowElevation: Dp = 0.dp,
    border: BorderStroke? = null,
    interactionSource: MutableInteractionSource? = null,
    indication: Indication? = null,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier.clickable(
            interactionSource = interactionSource,
            indication = indication,
            enabled = enabled,
            onClick = onClick,
            role = Role.Button
        ),
        shape = shape,
        color = color,
        contentColor = contentColor,
        shadowElevation = shadowElevation,
        border = border,
        contentPadding = contentPadding,
        content = content
    )
}

@Composable
@NonRestartableComposable
fun SelectableSurface(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RectangleShape,
    color: Color = Color.Unspecified,
    contentColor: Color = Color.Unspecified,
    shadowElevation: Dp = 0.dp,
    border: BorderStroke? = null,
    interactionSource: MutableInteractionSource? = null,
    indication: Indication? = null,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier.selectable(
            selected = selected,
            interactionSource = interactionSource,
            indication = indication,
            enabled = enabled,
            onClick = onClick
        ),
        shape = shape,
        color = color,
        contentColor = contentColor,
        shadowElevation = shadowElevation,
        border = border,
        contentPadding = contentPadding,
        content = content
    )
}

@Composable
@NonRestartableComposable
fun CheckableSurface(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RectangleShape,
    color: Color = Color.Unspecified,
    contentColor: Color = Color.Unspecified,
    shadowElevation: Dp = 0.dp,
    border: BorderStroke? = null,
    interactionSource: MutableInteractionSource? = null,
    indication: Indication? = null,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier.toggleable(
            value = checked,
            interactionSource = interactionSource,
            indication = indication,
            enabled = enabled,
            onValueChange = onCheckedChange
        ),
        shape = shape,
        color = color,
        contentColor = contentColor,
        shadowElevation = shadowElevation,
        border = border,
        contentPadding = contentPadding,
        content = content
    )
}

@Stable
private fun Modifier.surface(
    shape: Shape,
    backgroundColor: Color,
    border: BorderStroke?,
    shadowElevation: Float,
) =
    this
        .then(
            if (shadowElevation > 0f) {
                Modifier.graphicsLayer(
                    shadowElevation = shadowElevation,
                    shape = shape,
                    clip = false
                )
            } else {
                Modifier
            }
        )
        .then(if (border != null) Modifier.border(border, shape) else Modifier)
        .background(color = backgroundColor, shape = shape)
        .clip(shape)

@Preview(showBackground = true, name = "Default ModifSurface")
@Composable
fun SurfacePreview() {
    Surface {
        Text(text = "Default Surface")
    }
}

@Preview(showBackground = true, name = "Clickable ModifSurface")
@Composable
fun ClickableSurfacePreview() {
    ClickableSurface(onClick = {}) {
        Text(text = "Clickable Surface")
    }
}

@Preview(showBackground = true, name = "Selectable ModifSurface")
@Composable
fun SelectableModifSurfacePreview() {
    var selected by remember { mutableStateOf(false) }

    SelectableSurface(
        selected = selected,
        onClick = { selected = !selected }
    ) {
        Text(text = if (selected) "Selected" else "Not Selected")
    }
}

@Preview(showBackground = true, name = "Toggleable ModifSurface")
@Composable
fun ToggleableSurfacePreview() {
    var checked by remember { mutableStateOf(false) }

    CheckableSurface(
        checked = checked,
        onCheckedChange = { checked = it }
    ) {
        Text(text = if (checked) "Checked" else "Unchecked")
    }
}

@Preview(showBackground = true, name = "Glass Surface")
@Composable
fun GlassTestScreen() {
    val backdropState =
        rememberGlassBackdropState()

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        /*
         * Glass 뒤쪽 영역.
         *
         * Image뿐 아니라 Box 전체에 걸어도 됨.
         */
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .glassBackdrop(
                        backdropState,
                    ),
        ) {
            Image(
                painter =
                    painterResource(
                        R.drawable.img_challenge,
                    ),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier.fillMaxSize(),
            )
        }

        GlassSurface(
            backdropState = backdropState,
            modifier =
                Modifier
                    .align(Alignment.Center)
                    .size(
                        width = 280.dp,
                        height = 72.dp,
                    ),
            shape =
                RoundedCornerShape(
                    24.dp,
                ),
        ) {
            Box(
                modifier =
                    Modifier.fillMaxSize(),
                contentAlignment =
                    Alignment.Center,
            ) {
                Text(
                    text = "Glass Surface",
                )
            }
        }
    }
}

