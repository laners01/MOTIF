package com.ssafy.motif.core.designsystem.component

import androidx.compose.foundation.Indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ssafy.motif.core.designsystem.R
import com.ssafy.motif.core.designsystem.theme.LocalMotifContentColor
import com.ssafy.motif.core.designsystem.theme.MotifTheme

@Composable
fun IconButton(
    onClick: () -> Unit,
    imageVector: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = IconMinimumSize,
    contentDescription: String? = null,
    padding: PaddingValues = PaddingValues(0.dp),
    enabled: Boolean = true,
    colors: ModifIconButtonColors = ModifIconButtonDefaults.iconButtonColors(),
    interactionSource: MutableInteractionSource? = null,
    indication: Indication? = null,
) {
    IconButtonContainer(
        onClick = onClick,
        size = size,
        modifier = modifier,
        padding = padding,
        enabled = enabled,
        colors = colors,
        interactionSource = interactionSource,
        indication = indication,
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
        )
    }
}

@Composable
fun IconButton(
    onClick: () -> Unit,
    painter: Painter,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    enabled: Boolean = true,
    size: Dp = IconMinimumSize,
    padding: PaddingValues = PaddingValues(0.dp),
    colors: ModifIconButtonColors = ModifIconButtonDefaults.iconButtonColors(),
    interactionSource: MutableInteractionSource? = null,
    indication: Indication? = null,
) {
    IconButtonContainer(
        onClick = onClick,
        size = size,
        modifier = modifier,
        padding = padding,
        enabled = enabled,
        colors = colors,
        interactionSource = interactionSource,
        indication = indication,
    ) {
        Icon(
            painter = painter,
            contentDescription = contentDescription,
        )
    }
}

@Composable
private fun IconButtonContainer(
    onClick: () -> Unit,
    size: Dp,
    modifier: Modifier,
    padding: PaddingValues,
    enabled: Boolean,
    colors: ModifIconButtonColors,
    interactionSource: MutableInteractionSource? = null,
    indication: Indication? = null,
    content: @Composable () -> Unit
) {
    ClickableSurface(
        modifier = Modifier
            .size(size)
            .padding(padding)
            .then(modifier),
        onClick = {
            if (enabled) onClick()
        },
        color = if (enabled) colors.containerColor else colors.disabledContainerColor,
        interactionSource = interactionSource,
        indication = indication
    ) {
        val contentColor = if (enabled) colors.contentColor else colors.disabledContentColor
        CompositionLocalProvider(LocalMotifContentColor provides contentColor, content = content)
    }
}

@Preview(showBackground = true)
@Composable
private fun MotifIconButtonPreview() {
    MotifTheme {
        IconButton(
            onClick = {},
            painter = painterResource(R.drawable.ic_arrow_left_24),
        )
    }
}

private val IconMinimumSize: Dp = 16.dp

object ModifIconButtonDefaults {
    private const val DISABLED_OPACITY = 0.38f

    @Composable
    fun iconButtonColors(
        containerColor: Color = Color.Transparent,
        contentColor: Color = LocalMotifContentColor.current,
        disabledContainerColor: Color = Color.Transparent,
        disabledContentColor: Color = contentColor.copy(DISABLED_OPACITY),
    ): ModifIconButtonColors {
        val colors = ModifIconButtonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = disabledContainerColor,
            disabledContentColor = disabledContentColor
        )

        return if (colors.contentColor == contentColor) {
            colors
        } else {
            colors.copy(
                contentColor = contentColor,
                disabledContentColor = contentColor.copy(DISABLED_OPACITY)
            )
        }
    }
}

@Immutable
class ModifIconButtonColors(
    val containerColor: Color,
    val contentColor: Color,
    val disabledContainerColor: Color,
    val disabledContentColor: Color,
) {

    fun copy(
        containerColor: Color = this.containerColor,
        contentColor: Color = this.contentColor,
        disabledContainerColor: Color = this.disabledContainerColor,
        disabledContentColor: Color = this.disabledContentColor,
    ) =
        ModifIconButtonColors(
            containerColor.takeOrElse { this.containerColor },
            contentColor.takeOrElse { this.contentColor },
            disabledContainerColor.takeOrElse { this.disabledContainerColor },
            disabledContentColor.takeOrElse { this.disabledContentColor },
        )

    @Stable
    internal fun containerColor(enabled: Boolean): Color =
        if (enabled) containerColor else disabledContainerColor

    @Stable
    internal fun contentColor(enabled: Boolean): Color =
        if (enabled) contentColor else disabledContentColor

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || other !is ModifIconButtonColors) return false

        if (containerColor != other.containerColor) return false
        if (contentColor != other.contentColor) return false
        if (disabledContainerColor != other.disabledContainerColor) return false
        if (disabledContentColor != other.disabledContentColor) return false

        return true
    }

    override fun hashCode(): Int {
        var result = containerColor.hashCode()
        result = 31 * result + contentColor.hashCode()
        result = 31 * result + disabledContainerColor.hashCode()
        result = 31 * result + disabledContentColor.hashCode()

        return result
    }
}