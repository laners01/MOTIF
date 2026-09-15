package com.ssafy.motif.core.ui.motif

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ssafy.motif.core.designsystem.component.Text
import com.ssafy.motif.core.designsystem.theme.MotifTheme
import com.ssafy.motif.core.model.home.Artist
import com.ssafy.motif.core.model.home.Motif
import com.ssafy.motif.core.designsystem.R as DesignSystemR

@Composable
fun MotifCard(
    motif: Motif,
    onMotifClick: (Long) -> Unit,
    onArtistClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable {
                onMotifClick(motif.id)
            },
    ) {
        AsyncImage(
            model = motif.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
        )

        MotifInformation(
            motif = motif,
            onArtistClick = onArtistClick,
        )
    }
}

@Composable
private fun MotifInformation(
    motif: Motif,
    onArtistClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = 12.dp,
                vertical = 12.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = motif.artist.profileImageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .clickable {
                    onArtistClick(motif.artist.id)
                },
        )

        Spacer(
            modifier = Modifier.width(12.dp),
        )

        Column(
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = motif.title,
                color = MotifTheme.colors.typo,
                style = MotifTheme.typography.bodyR16,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Text(
                text = motif.artist.displayNameWithGroup(),
                color = MotifTheme.colors.gray500,
                style = MotifTheme.typography.bodyR12,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun Artist.displayNameWithGroup(): String =
    groupName
        ?.takeIf { it.isNotBlank() }
        ?.let { group ->
            "$name · $group"
        }
        ?: name

@Preview(name = "Motif Item", showBackground = true)
@Composable
private fun MotifCardPreview() {
    val context = LocalContext.current

    val previewImageUrl =
        "android.resource://${context.packageName}/${DesignSystemR.drawable.img_test_motif}"

    MotifTheme {
        MotifCard(
            motif = Motif(
                id = 1L,
                thumbnailUrl = previewImageUrl,
                title = "카페 맞은편 자리에 앉아 예지와 함께 여유로운 하루",
                artist = Artist(
                    id = 1L,
                    name = "YEJI",
                    profileImageUrl = previewImageUrl,
                    groupName = "ITZY",
                ),
                createdAt = 0L,
            ),
            onMotifClick = {},
            onArtistClick = {},
        )
    }
}