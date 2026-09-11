package com.ssafy.motif.feature.home.impl.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ssafy.motif.core.designsystem.theme.MotifTheme
import com.ssafy.motif.core.model.home.Artist
import com.ssafy.motif.core.model.home.Motif
import com.ssafy.motif.core.model.home.Notice
import com.ssafy.motif.core.ui.motif.MotifCard
import com.ssafy.motif.feature.home.impl.component.notice.NoticeCard
import com.ssafy.motif.feature.home.impl.model.MyArtistFeedItem
import com.ssafy.motif.core.designsystem.R as designSystemR

internal fun LazyListScope.myArtistFeed(
    items: List<MyArtistFeedItem>,
    onArtistClick: (Long) -> Unit,
    onMotifClick: (Long) -> Unit,
    onNoticeLikeClick: (Long) -> Unit,
    onNoticeCommentClick: (Long) -> Unit,
) {
    items(
        items = items,
        key = { item ->
            when (item) {
                is MyArtistFeedItem.NoticeItem ->
                    "notice-${item.notice.id}"

                is MyArtistFeedItem.MotifItem ->
                    "motif-${item.motif.id}"
            }
        },
        contentType = { item ->
            item::class
        },
    ) { item ->
        when (item) {
            is MyArtistFeedItem.NoticeItem -> {
                NoticeCard(
                    notice = item.notice,
                    onArtistClick = onArtistClick,
                    onLikeClick = onNoticeLikeClick,
                    onCommentClick = onNoticeCommentClick,
                    modifier = Modifier,
                )
            }

            is MyArtistFeedItem.MotifItem -> {
                MotifCard(
                    motif = item.motif,
                    onMotifClick = onMotifClick,
                    onArtistClick = onArtistClick,
                    modifier = Modifier,
                )
            }
        }
    }
}

@Preview(name = "My Artist Feed", showBackground = true)
@Composable
private fun MyArtistFeedPreview() {
    val context = LocalContext.current

    val noticeImageUrl =
        "android.resource://${context.packageName}/${designSystemR.drawable.img_challenge}"

    val motifImageUrl =
        "android.resource://${context.packageName}/${designSystemR.drawable.img_test_motif}"

    val artist = Artist(
        id = 1L,
        name = "YEJI",
        profileImageUrl = noticeImageUrl,
        groupName = "ITZY",
    )

    val items = listOf(
        MyArtistFeedItem.NoticeItem(
            notice = Notice(
                id = 1L,
                artist = artist,
                content = """
                        안녕하세요. 예지입니다.
                        블랙핑크 10주년을 기념하면서 블링크와 제대로 대화를 나누지 못해서 저도 많이 속상하게 생각하고 있었어요.
                        미리 예정되어 있던 페스티벌 마지막 공연까지 마치고 글을 남기는 게 제 마음이 더 잘 전달될 것 같아 올려봅니다.
                    """.trimIndent(),
                imageUrls = listOf(
                    noticeImageUrl,
                    noticeImageUrl,
                ),
                likeCount = 2_100L,
                commentCount = 1_100L,
                isLiked = false,
                createdAt = 3L,
            ),
        ),
        MyArtistFeedItem.MotifItem(
            motif = Motif(
                id = 1L,
                thumbnailUrl = motifImageUrl,
                title = "카페 맞은편 자리에 앉아 예지와 함께 여유로운 하루",
                artist = artist,
                createdAt = 2L,
            ),
        ),
        MyArtistFeedItem.NoticeItem(
            notice = Notice(
                id = 2L,
                artist = artist,
                content = "오늘도 좋은 하루 보내요 💛",
                imageUrls = emptyList(),
                likeCount = 842L,
                commentCount = 123L,
                isLiked = true,
                createdAt = 1L,
            ),
        ),
    )

    MotifTheme {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MotifTheme.colors.background),
            contentPadding = PaddingValues(
                horizontal = 16.dp,
                vertical = 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            myArtistFeed(
                items = items,
                onArtistClick = {},
                onMotifClick = {},
                onNoticeLikeClick = {},
                onNoticeCommentClick = {},
            )
        }
    }
}