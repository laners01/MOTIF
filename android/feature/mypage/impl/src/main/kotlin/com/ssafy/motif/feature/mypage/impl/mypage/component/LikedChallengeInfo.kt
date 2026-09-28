package com.ssafy.motif.feature.mypage.impl.mypage.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.ssafy.motif.core.designsystem.component.Icon
import com.ssafy.motif.core.designsystem.component.Text
import com.ssafy.motif.core.designsystem.icon.MotifIcons
import com.ssafy.motif.core.designsystem.theme.MotifTheme

/**
 * 캐러셀 중앙 카드의 부가 정보.
 *
 * 모델이 아닌 값을 받아 특정 화면 모델에 묶이지 않게 한다.
 */
@Composable
internal fun LikedChallengeInfo(
    likeCount: Long,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            painter = painterResource(MotifIcons.FilledHeart),
            contentDescription = null,
            tint = MotifTheme.colors.pink,
            modifier = Modifier.size(20.dp),
        )

        Text(
            text = formatLikeCount(likeCount),
            style = MotifTheme.typography.titleSB20,
            color = MotifTheme.colors.typo,
        )
    }
}

// core:ui의 formatCount는 k/m 표기라 쓰지 않는다.
// 챌린지 화면의 formatShortCount와 같은 규칙이다.
private fun formatLikeCount(
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
