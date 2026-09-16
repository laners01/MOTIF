package com.ssafy.motif.core.ui.challenges

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ssafy.motif.core.designsystem.component.HorizontalDivider
import com.ssafy.motif.core.designsystem.component.IconButton
import com.ssafy.motif.core.designsystem.component.ModifIconButtonDefaults
import com.ssafy.motif.core.designsystem.component.Surface
import com.ssafy.motif.core.designsystem.component.Text
import com.ssafy.motif.core.designsystem.component.bottomsheet.ModalBottomSheet
import com.ssafy.motif.core.designsystem.component.bottomsheet.SheetState
import com.ssafy.motif.core.designsystem.component.bottomsheet.rememberModalBottomSheetState
import com.ssafy.motif.core.designsystem.icon.MotifIcons
import com.ssafy.motif.core.designsystem.theme.MotifTheme
import com.ssafy.motif.core.model.challenges.ChallengeShortForm
import com.ssafy.motif.core.model.home.Artist
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import com.ssafy.motif.core.designsystem.R as DesignSystemR

@Composable
fun ChallengeDescriptionBottomSheet(
    challenge: ChallengeShortForm,
    onDismissRequest: () -> Unit,
    onArtistClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState =
        rememberModalBottomSheetState(
            skipPartiallyExpanded = false,
            partiallyExpandedFraction =
                DESCRIPTION_SHEET_FRACTION,
        ),
) {
    val coroutineScope =
        rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        dragHandle = null,
        modifier = modifier,
    ) {
        ChallengeDescriptionContent(
            challenge = challenge,
            onDismissClick = {
                coroutineScope.launch {
                    sheetState.hide()
                }
            },
            onArtistClick = onArtistClick,
        )
    }
}

@Composable
private fun ChallengeDescriptionContent(
    challenge: ChallengeShortForm,
    onDismissClick: () -> Unit,
    onArtistClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        DescriptionSheetHeader(
            onDismissClick = onDismissClick,
        )

        HorizontalDivider(
            color = MotifTheme.colors.gray500,
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
        ) {
            Text(
                text = challenge.description,
                color = MotifTheme.colors.typo,
                style = MotifTheme.typography.bodyR16,
            )

            Spacer(
                modifier = Modifier.height(24.dp),
            )

            ChallengeStatistics(
                likeCount = challenge.likeCount,
                viewCount = challenge.viewCount,
                createdAt = challenge.createdAt,
            )

            if (
                challenge.challengeArtists
                    .isNotEmpty()
            ) {
                Spacer(
                    modifier = Modifier.height(20.dp),
                )

                ChallengeArtistList(
                    artists =
                        challenge.challengeArtists,
                    onArtistClick =
                        onArtistClick,
                )
            }
        }
    }
}

@Composable
private fun DescriptionSheetHeader(
    onDismissClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(20.dp),
        verticalAlignment =
            Alignment.CenterVertically,
    ) {
        Text(
            text = "설명",
            color = MotifTheme.colors.typo,
            style = MotifTheme.typography.titleSB18,
        )

        Spacer(
            modifier = Modifier.weight(1f),
        )

        IconButton(
            onClick = onDismissClick,
            painter = painterResource(
                MotifIcons.Close,
            ),
            contentDescription = "닫기",
            size = 24.dp,
            colors =
                ModifIconButtonDefaults
                    .iconButtonColors(
                        contentColor =
                            MotifTheme.colors.typo,
                    ),
        )
    }
}

@Composable
private fun ChallengeStatistics(
    likeCount: Long,
    viewCount: Long,
    createdAt: Long,
    modifier: Modifier = Modifier,
) {
    val date =
        remember(createdAt) {
            Instant
                .ofEpochMilli(
                    createdAt,
                )
                .atZone(
                    ZoneId.systemDefault(),
                )
                .toLocalDate()
        }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement =
            Arrangement.spacedBy(8.dp),
    ) {
        ChallengeStatisticItem(
            value =
                formatLikeCount(
                    likeCount,
                ),
            label = "좋아요",
            modifier =
                Modifier.weight(1f),
        )

        ChallengeStatisticItem(
            value =
                formatViewCount(
                    viewCount,
                ),
            label = "조회수",
            modifier =
                Modifier.weight(1f),
        )

        ChallengeStatisticItem(
            value =
                "${date.monthValue}월 ${date.dayOfMonth}일",
            label =
                "${date.year}년",
            modifier =
                Modifier.weight(1f),
        )
    }
}

@Composable
private fun ChallengeStatisticItem(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(
                RoundedCornerShape(
                    12.dp,
                ),
            )
            .background(
                MotifTheme.colors.black,
            )
            .padding(
                vertical = 8.dp,
            ),
        horizontalAlignment =
            Alignment.CenterHorizontally,
        verticalArrangement =
            Arrangement.Center,
    ) {
        Text(
            text = value,
            color = MotifTheme.colors.typo,
            style =
                MotifTheme.typography.titleSB20,
            maxLines = 1,
        )

        Spacer(
            modifier = Modifier.height(2.dp),
        )

        Text(
            text = label,
            color = MotifTheme.colors.gray300,
            style =
                MotifTheme.typography.bodySB12,
            maxLines = 1,
        )
    }
}

@Composable
private fun ChallengeArtistList(
    artists: List<Artist>,
    onArtistClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement =
            Arrangement.spacedBy(8.dp),
    ) {
        items(
            items = artists,
            key = Artist::id,
        ) { artist ->
            ChallengeArtistItem(
                artist = artist,
                onClick = {
                    onArtistClick(
                        artist.id,
                    )
                },
            )
        }
    }
}

@Composable
private fun ChallengeArtistItem(
    artist: Artist,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(72.dp)
            .clickable(
                interactionSource =
                    remember {
                        MutableInteractionSource()
                    },
                indication = null,
                onClick = onClick,
            ),
        horizontalAlignment =
            Alignment.CenterHorizontally,
    ) {
        AsyncImage(
            model = artist.profileImageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape),
        )

        Spacer(
            modifier = Modifier.height(8.dp),
        )

        Text(
            text = artist.name,
            color = MotifTheme.colors.typo,
            style = MotifTheme.typography.bodyR14,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier =
                Modifier.fillMaxWidth(),
        )
    }
}

private fun formatLikeCount(
    count: Long,
): String =
    when {
        count >= 100_000_000 -> {
            val value =
                count / 100_000_000f

            if (value % 1f == 0f) {
                "${value.toInt()}억"
            } else {
                "%.1f억".format(value)
            }
        }

        count >= 10_000 -> {
            val value =
                count / 10_000f

            if (value % 1f == 0f) {
                "${value.toInt()}만"
            } else {
                "%.1f만".format(value)
            }
        }

        else -> count.toString()
    }

private fun formatViewCount(
    count: Long,
): String =
    "%,d".format(count)

private const val DESCRIPTION_SHEET_FRACTION =
    0.45f

@Preview(
    name = "Challenge Description Sheet Content",
    showBackground = true,
)
@Composable
private fun ChallengeDescriptionSheetContentPreview() {
    val context =
        LocalContext.current

    val profileUrl =
        "android.resource://${context.packageName}/${DesignSystemR.drawable.img_profile}"

    MotifTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    MotifTheme.colors.black,
                ),
        ) {
            Surface(
                modifier = Modifier
                    .align(
                        Alignment.BottomCenter,
                    )
                    .fillMaxWidth(),
                shape =
                    RoundedCornerShape(
                        topStart = 28.dp,
                        topEnd = 28.dp,
                    ),
                color =
                    MotifTheme.colors.background,
                contentColor =
                    MotifTheme.colors.typo,
            ) {
                ChallengeDescriptionContent(
                    challenge =
                        previewChallenge(
                            profileUrl =
                                profileUrl,
                        ),
                    onDismissClick = {},
                    onArtistClick = {},
                    modifier =
                        Modifier.padding(
                            bottom = 24.dp,
                        ),
                )
            }
        }
    }
}

private fun previewChallenge(
    profileUrl: String,
): ChallengeShortForm =
    ChallengeShortForm(
        id = 1L,
        videoUrl = "",
        thumbnailUrl = "",
        artist =
            Artist(
                id = 4L,
                name = "예지",
                profileImageUrl =
                    profileUrl,
                groupName = "ITZY",
            ),
        description = """
            이번 챌린지 포인트는 손동작이랑 마지막 표정! 🔥
            생각보다 어렵지 않으니까 같이 도전해봐요 🖤
            여러분이 만든 영상도 보러 갈게요 :)
        """.trimIndent(),
        songArtistName =
            "mochico",
        songTitle =
            "bailar toda la noche",
        likeCount =
            12_000L,
        commentCount =
            2_400L,
        participantCount =
            800L,
        viewCount =
            617_988L,
        createdAt =
            1_788_105_600_000L,
        isLiked =
            false,
        isSubscribed =
            false,
        challengeArtists =
            listOf(
                Artist(
                    id = 1L,
                    name = "채령",
                    profileImageUrl =
                        profileUrl,
                    groupName = "ITZY",
                ),
                Artist(
                    id = 2L,
                    name = "이채영",
                    profileImageUrl =
                        profileUrl,
                    groupName = null,
                ),
                Artist(
                    id = 3L,
                    name = "오해원",
                    profileImageUrl =
                        profileUrl,
                    groupName = "NMIXX",
                ),
                Artist(
                    id = 4L,
                    name = "예지",
                    profileImageUrl =
                        profileUrl,
                    groupName = "ITZY",
                ),
            ),
    )