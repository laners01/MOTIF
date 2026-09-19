package com.ssafy.motif.feature.motif.impl.archive.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ssafy.motif.core.designsystem.component.Text
import com.ssafy.motif.core.designsystem.theme.MotifTheme
import com.ssafy.motif.core.model.motif.SavedMotif
import com.ssafy.motif.core.ui.formatter.formatDotDate
import com.ssafy.motif.feature.motif.impl.archive.ArchiveMockData

/**
 * 보관함 목록의 저장 모티프 한 항목. 썸네일 + 저장일 + 설명.
 *
 * @param selected 상단 재생 영역에서 재생 중인 항목인지. 썸네일에 핑크 테두리가 표시된다.
 * @param onClick 항목 선택 → 상단 재생 대상 변경.
 */
@Composable
internal fun SavedMotifItem(
    motif: SavedMotif,
    selected: Boolean,
    onClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor by animateColorAsState(
        targetValue = if (selected) MotifTheme.colors.pink else Color.Transparent,
        label = "saved motif border",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(role = Role.Button) { onClick(motif.id) },
        verticalAlignment = Alignment.Top,
    ) {
        AsyncImage(
            model = motif.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .width(ThumbnailWidth)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(ThumbnailCornerRadius))
                .border(
                    width = SelectedBorderWidth,
                    color = borderColor,
                    shape = RoundedCornerShape(ThumbnailCornerRadius),
                ),
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = motif.savedAt.formatDotDate(),
                color = MotifTheme.colors.gray500,
                style = MotifTheme.typography.captionR12,
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = motif.description,
                color = MotifTheme.colors.typo,
                style = MotifTheme.typography.bodyR14,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private val ThumbnailWidth = 178.dp
private val ThumbnailCornerRadius = 8.dp
private val SelectedBorderWidth = 2.dp

@Preview(name = "Selected", showBackground = true)
@Composable
private fun SavedMotifItemSelectedPreview() {
    MotifTheme {
        SavedMotifItem(
            motif = ArchiveMockData.savedMotifs[0],
            selected = true,
            onClick = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SavedMotifItemPreview() {
    MotifTheme {
        SavedMotifItem(
            motif = ArchiveMockData.savedMotifs[1],
            selected = false,
            onClick = {},
        )
    }
}
