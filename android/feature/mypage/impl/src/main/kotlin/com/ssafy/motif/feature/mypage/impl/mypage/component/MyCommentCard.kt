package com.ssafy.motif.feature.mypage.impl.mypage.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.ssafy.motif.core.designsystem.component.Icon
import com.ssafy.motif.core.designsystem.component.Text
import com.ssafy.motif.core.designsystem.icon.MotifIcons
import com.ssafy.motif.core.designsystem.theme.MotifTheme
import com.ssafy.motif.core.model.mypage.LikedChallengeComment
import com.ssafy.motif.core.ui.formatter.formatCount
import com.ssafy.motif.core.ui.formatter.formatCreatedAt

@Composable
internal fun MyCommentCard(
    myComment: LikedChallengeComment,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val comment = myComment.comment

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MotifTheme.colors.black)
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = comment.content,
                style = MotifTheme.typography.bodyR14,
                color = MotifTheme.colors.typo,
                // 본문이 길면 시간이 다음 줄로 밀리도록 남는 폭만 차지한다.
                modifier = Modifier.weight(1f, fill = false),
            )
            Text(
                text = comment.createdAt.formatCreatedAt(),
                style = MotifTheme.typography.captionR12,
                color = MotifTheme.colors.gray400,
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            CommentCount(
                iconRes = MotifIcons.Heart,
                count = comment.likeCount,
            )

            CommentCount(
                iconRes = MotifIcons.Message,
                count = comment.replyCount,
            )
        }
        if (myComment.replies.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))

            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                myComment.replies
                    .take(MAX_VISIBLE_REPLIES)
                    .forEach { reply ->
                        ReplyItem(reply = reply)
                    }
            }
        }
    }
}
@Composable
private fun CommentCount(
    @DrawableRes iconRes: Int,
    count: Long,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
        ,
        horizontalArrangement = Arrangement.spacedBy(6
            .dp),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = MotifTheme.colors.typo,
            modifier = Modifier.size(20.dp),
        )

        Text(
            text = count.formatCount(),
            style = MotifTheme.typography.bodyR14,
            color = MotifTheme.colors.typo,
        )
    }
}

private const val MAX_VISIBLE_REPLIES = 2