package com.ssafy.motif.feature.challenges.impl.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.ssafy.motif.core.designsystem.component.Icon
import com.ssafy.motif.core.designsystem.component.Text
import com.ssafy.motif.core.designsystem.theme.MotifTheme

@Composable
internal fun ShortFormAction(
    @DrawableRes iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    count: Long? = null,
    tint: Color = Color.White,
) {
    Column(
        modifier = modifier.clickable(
            interactionSource = remember {
                MutableInteractionSource()
            },
            indication = null,
            onClick = onClick,
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(28.dp),
        )

        count?.let {
            Spacer(
                modifier = Modifier.height(4.dp),
            )

            Text(
                text = formatShortCount(it),
                color = Color.White,
                style = MotifTheme.typography.bodyR16,
            )
        }
    }
}

private fun formatShortCount(
    count: Long,
): String =
    when {
        count >= 10_000 -> {
            val value = count / 10_000f

            if (value % 1f == 0f) {
                "${value.toInt()}만"
            } else {
                "%.1f만".format(value)
            }
        }

        count >= 1_000 -> {
            val value = count / 1_000f

            if (value % 1f == 0f) {
                "${value.toInt()}천"
            } else {
                "%.1f천".format(value)
            }
        }

        else -> count.toString()
    }