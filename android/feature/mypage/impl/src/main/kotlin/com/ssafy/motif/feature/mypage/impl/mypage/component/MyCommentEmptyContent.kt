package com.ssafy.motif.feature.mypage.impl.mypage.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ssafy.motif.core.designsystem.component.Text
import com.ssafy.motif.core.designsystem.theme.MotifTheme

/**
 * 해당 챌린지에 작성한 댓글이 없을 때의 댓글 영역.
 *
 * MyCommentCard와 같은 박스를 쓰고 안내 문구만 가운데에 둔다.
 */
@Composable
internal fun MyCommentEmptyContent(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MotifTheme.colors.black)
            // 박스 높이를 만드는 값. 시안에 맞춰 조정할 것
            .padding(horizontal = 16.dp, vertical = 40.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "해당 챌린지에 작성한 댓글이 없어요.",
            style = MotifTheme.typography.bodyR14,
            color = MotifTheme.colors.gray400,
            textAlign = TextAlign.Center,
        )
    }
}
