package com.ssafy.motif.core.ui.notice

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import com.ssafy.motif.core.designsystem.R as DesignSystemR

@Composable
internal fun NoticeActions(
    likeCount: Long,
    commentCount: Long,
    isLiked: Boolean,
    onLikeClick: () -> Unit,
    onCommentClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 16.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NoticeAction(
            iconRes =
                if (isLiked) {
                    DesignSystemR.drawable.ic_filled_heart_24
                } else {
                    DesignSystemR.drawable.ic_heart_24
                },
            count = likeCount,
            tint =
                if (isLiked) {
                    NoticeDefaults.AccentColor
                } else {
                    MotifTheme.colors.typo
                },
            onClick = onLikeClick,
        )

        Spacer(
            modifier = Modifier.width(12.dp),
        )

        NoticeAction(
            iconRes = DesignSystemR.drawable.ic_message_24,
            count = commentCount,
            tint = MotifTheme.colors.typo,
            onClick = onCommentClick,
        )
    }
}

@Composable
private fun NoticeAction(
    iconRes: Int,
    count: Long,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.clickable(
            interactionSource = remember {
                MutableInteractionSource()
            },
            indication = null,
            onClick = onClick,
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(
                iconRes,
            ),
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = tint,
        )

        Spacer(
            modifier = Modifier.width(6.dp),
        )

        Text(
            text = formatCount(count),
            color = MotifTheme.colors.typo,
            style = MotifTheme.typography.bodySB12,
        )
    }
}

private fun formatCount(
    count: Long,
): String =
    when {
        count >= 1_000_000 -> {
            val value =
                count / 1_000_000f

            if (value % 1f == 0f) {
                "${value.toInt()}m"
            } else {
                "%.1fm".format(value)
            }
        }

        count >= 1_000 -> {
            val value =
                count / 1_000f

            if (value % 1f == 0f) {
                "${value.toInt()}k"
            } else {
                "%.1fk".format(value)
            }
        }

        else -> count.toString()
    }