package com.ssafy.motif.core.ui.notice

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ssafy.motif.core.designsystem.component.HorizontalDivider
import com.ssafy.motif.core.designsystem.component.Icon
import com.ssafy.motif.core.designsystem.component.Text
import com.ssafy.motif.core.designsystem.theme.MotifTheme
import com.ssafy.motif.core.model.artist.Artist
import com.ssafy.motif.core.model.home.Notice
import com.ssafy.motif.core.designsystem.R as DesignSystemR

@Composable
fun NoticeCard(
    notice: Notice,
    onArtistClick: (Long) -> Unit,
    onLikeClick: (Long) -> Unit,
    onCommentClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable(notice.id) {
        mutableStateOf(false)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (expanded) {
                    Modifier
                } else {
                    Modifier.heightIn(max = NoticeDefaults.CollapsedHeight)
                },
            )
            .shadow(
                elevation = 4.dp,
                shape = NoticeDefaults.Shape,
                clip = false,
                ambientColor = NoticeDefaults.ShadowColor,
                spotColor = NoticeDefaults.ShadowColor,
            )
            .background(
                color = MotifTheme.colors.background,
                shape = NoticeDefaults.Shape,
            )
            .border(
                width = 1.dp,
                color = NoticeDefaults.BorderColor,
                shape = NoticeDefaults.Shape,
            )
            .clip(NoticeDefaults.Shape),
    ) {
        NoticeArtistHeader(
            notice = notice,
            onArtistClick = onArtistClick,
        )

        if (expanded) {
            NoticeExpandedContent(
                content = notice.content,
            )
        } else {
            NoticeCollapsedContent(
                content = notice.content,
                onExpandClick = {
                    expanded = true
                },
                modifier = Modifier.weight(
                    weight = 1f,
                    fill = false
                ),
            )
        }

        if (notice.imageUrls.isNotEmpty()) {
            NoticeImagePager(
                imageUrls = notice.imageUrls,
            )
        }

        HorizontalDivider()

        NoticeActions(
            likeCount = notice.likeCount,
            commentCount = notice.commentCount,
            isLiked = notice.isLiked,
            onLikeClick = {
                onLikeClick(notice.id)
            },
            onCommentClick = {
                onCommentClick(notice.id)
            },
        )
    }
}

@Composable
private fun NoticeArtistHeader(
    notice: Notice,
    onArtistClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable {
                onArtistClick(notice.artist.id)
            }
            .padding(
                horizontal = 16.dp,
                vertical = 16.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = notice.artist.profileImageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape),
        )

        Spacer(
            modifier = Modifier.width(12.dp),
        )

        Text(
            text = notice.artist.name,
            color = MotifTheme.colors.typo,
            style = MotifTheme.typography.bodySB16,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(
            modifier = Modifier.width(6.dp),
        )

        Icon(
            painter = painterResource(
                DesignSystemR.drawable.ic_artist_check_24,
            ),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = Color.Unspecified,
        )
    }
}

@Preview(name = "Notice Item", showBackground = true)
@Composable
private fun NoticeCardPreview() {
    val context = LocalContext.current

    val previewImageUrl =
        "android.resource://${context.packageName}/${DesignSystemR.drawable.img_challenge}"

    MotifTheme {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    MotifTheme.colors.background,
                )
                .padding(16.dp),
        ) {
            NoticeCard(
                notice = Notice(
                    id = 1L,
                    artist = Artist(
                        id = 1L,
                        name = "Yeji",
                        profileImageUrl = previewImageUrl,
                        groupName = "ITZY",
                    ),
                    content = """
                        안녕하세요. 예지입니다.
                        블랙핑크 10주년을 기념하면서 블링크와 제대로 대화를 나누지 못해서 저도 많이 속상하게 생각하고 있었어요.
                        미리 예정되어 있던 페스티벌 마지막 공연까지 마치고 글을 남기는 게 제 마음이 더 잘 전달될 것 같아 올려봅니다.
                    """.trimIndent(),
                    imageUrls = listOf(
                        previewImageUrl,
                        previewImageUrl,
                    ),
                    likeCount = 2_100L,
                    commentCount = 1_100L,
                    isLiked = false,
                    createdAt = 0L,
                ),
                onArtistClick = {},
                onLikeClick = {},
                onCommentClick = {},
            )
        }
    }
}