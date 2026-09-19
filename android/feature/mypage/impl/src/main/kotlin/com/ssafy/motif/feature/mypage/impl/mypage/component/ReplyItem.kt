package com.ssafy.motif.feature.mypage.impl.mypage.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ssafy.motif.core.designsystem.component.Text
import com.ssafy.motif.core.designsystem.theme.MotifTheme
import com.ssafy.motif.core.model.comment.Reply
import com.ssafy.motif.core.ui.formatter.formatCreatedAt
import com.ssafy.motif.core.designsystem.R as DesignSystemR


@Composable
internal fun ReplyItem(
    reply: Reply,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        AsyncImage(
            model = reply.profileImageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            placeholder = painterResource(DesignSystemR.drawable.profile_square),
            error = painterResource(DesignSystemR.drawable.profile_square),
            fallback = painterResource(DesignSystemR.drawable.profile_square),
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape),
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = reply.nickname,
                    style = MotifTheme.typography.bodySB14,
                    color = MotifTheme.colors.typo,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    // 닉네임이 길어도 시간이 밀려나지 않게 한다.
                            modifier = Modifier.weight(1f, fill = false),
                )

                Text(
                    text = reply.createdAt.formatCreatedAt(),
                    style = MotifTheme.typography.captionR12,
                    color = MotifTheme.colors.gray400,
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = reply.content,
                style = MotifTheme.typography.bodyR14,
                color = MotifTheme.colors.typo,
            )
        }
    }
}