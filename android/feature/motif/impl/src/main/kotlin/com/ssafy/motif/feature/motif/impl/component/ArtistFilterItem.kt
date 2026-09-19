package com.ssafy.motif.feature.motif.impl.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ssafy.motif.core.designsystem.component.Text
import com.ssafy.motif.core.designsystem.theme.MotifTheme
import com.ssafy.motif.core.model.home.Artist

/**
 * 원형 프로필 + 이름으로 구성된 아티스트 필터 항목. 선택되면 프로필에 핑크 테두리가 표시된다.
 * 에셋 목록·보관함 등 아티스트로 목록을 거르는 화면에서 공용으로 쓴다.
 */
@Composable
internal fun ArtistFilterItem(
    artist: Artist,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = ArtistFilterItemDefaults.Size,
) {
    val borderColor by animateColorAsState(
        targetValue = if (selected) MotifTheme.colors.pink else Color.Transparent,
        label = "artist filter border",
    )

    Column(
        modifier = modifier
            .width(size)
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AsyncImage(
            model = artist.profileImageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .border(
                    width = ArtistFilterItemDefaults.BorderWidth,
                    color = borderColor,
                    shape = CircleShape,
                ),
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = artist.name,
            color = MotifTheme.colors.typo,
            style = MotifTheme.typography.captionM12,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

internal object ArtistFilterItemDefaults {
    val Size: Dp = 70.dp
    val BorderWidth: Dp = 2.dp
}

@Preview(showBackground = true)
@Composable
private fun ArtistFilterItemPreview() {
    MotifTheme {
        ArtistFilterItem(
            artist = Artist(id = 1L, name = "카리나", profileImageUrl = "", groupName = "aespa"),
            selected = true,
            onClick = {},
        )
    }
}
