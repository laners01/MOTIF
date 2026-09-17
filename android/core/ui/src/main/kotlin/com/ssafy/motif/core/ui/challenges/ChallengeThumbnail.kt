package com.ssafy.motif.core.ui.challenges

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ssafy.motif.core.designsystem.component.Icon
import com.ssafy.motif.core.designsystem.component.Text
import com.ssafy.motif.core.designsystem.icon.MotifIcons
import com.ssafy.motif.core.designsystem.theme.MotifTheme

@Composable
fun ChallengeThumbnail(
    thumbnailUrl: String,
    viewCount: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
){
    Box(
        modifier = modifier
            .aspectRatio(9f/16f)
            .clickable(onClick = onClick),
    ){
        AsyncImage(
            model = thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize(),
        )
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                painter = painterResource(MotifIcons.FilledPlay),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(8.dp),
            )
            Text(
                text = formatViewCount(viewCount),
                style = MotifTheme.typography.captionSB8,
                color = Color.White,
            )
        }
    }
}

private fun formatViewCount(count: Long): String = when {
    count >= 1_000_000 -> {
        val value = count / 1_000_000f
        if (value % 1f == 0f) "${value.toInt()}M" else "%.1fM".format(value)
    }
    count >= 1_000 -> {
        val value = count / 1_000f
        if (value % 1f == 0f) "${value.toInt()}K" else "%.1fK".format(value)
    }
    else -> count.toString()
}
