package com.ssafy.motif.feature.ranking.impl.ranking

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.ssafy.motif.core.designsystem.component.Icon
import com.ssafy.motif.core.designsystem.component.IconButton
import com.ssafy.motif.core.designsystem.component.ModifIconButtonDefaults
import com.ssafy.motif.core.designsystem.component.Surface
import com.ssafy.motif.core.designsystem.component.Text
import com.ssafy.motif.core.designsystem.icon.MotifIcons
import com.ssafy.motif.core.designsystem.theme.MotifTheme
import com.ssafy.motif.core.model.artist.Artist
import com.ssafy.motif.core.model.challenge.ChallengeRank
import com.ssafy.motif.core.model.challenge.ChallengeSong


@Composable
fun RankingScreen(
    modifier: Modifier = Modifier,
    viewModel: RankingViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    RankingScreen(
        uiState = uiState,
        onArtistClick = viewModel::showArtistDetail,
        onQueryChange = viewModel::updateQuery,
        onRemoveClick = viewModel::removeQuery,
        onBackClick = viewModel::goBackScreen,
        onPlayClick = viewModel::playChallenge,
        modifier = modifier,
    )
}

@Composable
internal fun RankingScreen(
    uiState: RankingUiState,
    onPlayClick: (Long) -> Unit,    //재생버튼 클릭시
    onArtistClick: (Long) -> Unit,   //아티스트 클릭시
    onQueryChange: (String) -> Unit, //검색어 변경시
    onRemoveClick: () -> Unit,       //검색어 삭제
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.padding(start = 20.dp, top = 23.dp, bottom = 23.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            IconButton(
                painter = painterResource(MotifIcons.BarArrowLeft),
                onClick = onBackClick,
                size = 18.dp,
                colors = ModifIconButtonDefaults.iconButtonColors(
                    contentColor = MotifTheme.colors.typo
                ),
            )
            Text(
                text = "Who's on",
                style = MotifTheme.typography.bodySB16.copy(lineHeight = 19.sp),
                color = MotifTheme.colors.typo,
                maxLines = 1,
                overflow = TextOverflow.Visible,
                modifier = Modifier
            )

        }

        ArtistSearchBar(
            query = uiState.query,
            onQueryChange = onQueryChange,
            onRemoveClick = onRemoveClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp),
        )

        Box(modifier = Modifier.weight(1f)) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(1),
                contentPadding = PaddingValues(
                    top = 24.dp,
                    start = 20.dp,
                    end = 20.dp,
                    bottom = 20.dp
                ),
                verticalArrangement = Arrangement.spacedBy(20.dp),       //사이 상하간격
                modifier = Modifier.fillMaxSize()
            )

            {
                items(
                    items = uiState.filteredChallengeCard,
                    key = { it.challengeId }) { challenge ->
                    ArtistRankCard(
                        challenge = challenge,
                        onPlayClick = { onPlayClick(challenge.challengeId) },
                        onArtistClick = { onArtistClick(challenge.artist.id) },
                    )
                }

            }
            if (uiState.query.isNotBlank() && uiState.filteredChallengeCard.isEmpty()) {
                Text(
                    text = "검색 결과가 없습니다",
                    style = MotifTheme.typography.bodyR14,
                    color = MotifTheme.colors.gray400,
                    modifier = Modifier
                        .align(Alignment.Center),
                )
            }
        }

    }


}

@Composable
fun ArtistRankCard(
    challenge: ChallengeRank,
    onPlayClick: () -> Unit,
    onArtistClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(7.dp),
            modifier = Modifier.width(24.dp),
        ) {
            Text(
                text = "${challenge.rank}",
                style = MotifTheme.typography.titleSB20,
                color = MotifTheme.colors.typo,
                maxLines = 1,
                overflow = TextOverflow.Visible,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Icon(
                    painter = if (challenge.rankDelta > 0) {
                        painterResource(MotifIcons.RankingUpper)
                    } else if (challenge.rankDelta < 0) {
                        painterResource(MotifIcons.RankingLower)
                    } else {
                        painterResource(MotifIcons.RankingUnchanged)
                    },
                    contentDescription = null,
                    tint = if (challenge.rankDelta > 0) {
                        MotifTheme.colors.pink
                    } else if (challenge.rankDelta < 0) {
                        MotifTheme.colors.gray500
                    } else {
                        MotifTheme.colors.gray500
                    },
                    modifier = if (challenge.rankDelta !in 0..0) {
                        Modifier
                            .height(7.dp)
                            .width(6.dp)
                    } else {
                        Modifier
                            .height(2.dp)
                            .width(12.dp)
                    },

                    )


                if (challenge.rankDelta != 0L) {
                    Text(
                        text = if (challenge.rankDelta < 0) {
                            "${challenge.rankDelta}".drop(1)
                        } else {
                            "${challenge.rankDelta}"
                        },
                        style = MotifTheme.typography.captionSB8,
                        color = MotifTheme.colors.typo,
                        maxLines = 1,
                        overflow = TextOverflow.Visible,
                    )
                }

            }


        }


        AsyncImage(
            model = challenge.artist.profileImageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            placeholder =
                painterResource(com.ssafy.motif.core.designsystem.R.drawable.profile_square),
            error =
                painterResource(com.ssafy.motif.core.designsystem.R.drawable.profile_square),
            fallback =
                painterResource(com.ssafy.motif.core.designsystem.R.drawable.profile_square),

            modifier = Modifier
                .width(48.dp)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(4.dp))
                .clickable(onClick = onArtistClick),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = challenge.song.title,
                style = MotifTheme.typography.bodySB16,
                color = MotifTheme.colors.typo,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Text(
                text = challenge.artist.groupName ?: "",
                style = MotifTheme.typography.captionSB10,
                color = MotifTheme.colors.gray500,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

        }
        Text(
            text = challenge.song.title,
            style = MotifTheme.typography.bodySB16,
            color = MotifTheme.colors.typo,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )

        Box(
            modifier = Modifier
                .size(20.dp)
                .clickable(onClick = onPlayClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(MotifIcons.PlayOutline),
                contentDescription = null,
                tint = MotifTheme.colors.pink,
                modifier = Modifier
                    .height(18.dp)
                    .width(14.dp),
            )
        }


    }
}


@Composable
private fun ArtistSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onRemoveClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = MotifTheme.colors.gray700,
        contentPadding = PaddingValues(horizontal = 7.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(MotifIcons.Search),
                null,
                tint = MotifTheme.colors.typo,
                modifier = Modifier.size(20.dp)
            )

            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MotifTheme.typography.bodyR14.copy(
                    color = MotifTheme.colors.typo,
                ),
                cursorBrush = SolidColor(MotifTheme.colors.typo),
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
                decorationBox = { innerTextField ->
                    if (query.isEmpty()) {
                        Text(
                            text = "함께하고 싶은 멤버나 그룹을 찾아보세요",
                            style = MotifTheme.typography.bodyR16,
                            color = MotifTheme.colors.typo,
                        )
                    }
                    innerTextField()
                },
            )
            IconButton(
                painter = painterResource(MotifIcons.Close),
                onClick = onRemoveClick,
                colors = ModifIconButtonDefaults.iconButtonColors(
                    contentColor = MotifTheme.colors.typo
                ),
                modifier = Modifier
                    .size(20.dp)
                    .padding(end = 5.dp)
            )

        }
    }
}

internal object RankingMockData {

    /** rankDelta: 양수는 순위 상승, 음수는 하락, 0은 유지를 뜻한다. */
    val ranking: List<ChallengeRank> = listOf(
        ChallengeRank(
            challengeId = 1L,
            rank = 1L,
            artist = Artist(
                id = 1L,
                name = "이채영",
                profileImageUrl = "",
                groupName = "프로미스나인",
            ),
            song = ChallengeSong(
                title = "Supersonic",
                artistName = "프로미스나인",
            ),
            rankDelta = 20L,
        ),
        ChallengeRank(
            challengeId = 2L,
            rank = 2L,
            artist = Artist(
                id = 2L,
                name = "박지원",
                profileImageUrl = "",
                groupName = "프로미스나인",
            ),
            song = ChallengeSong(
                title = "Supersonic",
                artistName = "프로미스나인",
            ),
            rankDelta = -1L,
        ),
        ChallengeRank(
            challengeId = 3L,
            rank = 3L,
            artist = Artist(
                id = 3L,
                name = "김민주",
                profileImageUrl = "",
                groupName = "아이브",
            ),
            song = ChallengeSong(
                title = "Rebel Heart",
                artistName = "아이브",
            ),
            rankDelta = 0L,
        ),
        ChallengeRank(
            challengeId = 4L,
            rank = 4L,
            artist = Artist(
                id = 4L,
                name = "정하윤",
                profileImageUrl = "",
                groupName = "르세라핌",
            ),
            song = ChallengeSong(
                title = "Crazy",
                artistName = "르세라핌",
            ),
            rankDelta = 5L,
        ),
        ChallengeRank(
            challengeId = 5L,
            rank = 5L,
            artist = Artist(
                id = 5L,
                name = "최서연",
                profileImageUrl = "",
                groupName = "뉴진스",
            ),
            song = ChallengeSong(
                title = "How Sweet",
                artistName = "뉴진스",
            ),
            rankDelta = -3L,
        ),
        ChallengeRank(
            challengeId = 6L,
            rank = 6L,
            artist = Artist(
                id = 6L,
                name = "한소희",
                profileImageUrl = "",
                groupName = "아이들",
            ),
            song = ChallengeSong(
                title = "Klaxon",
                artistName = "아이들",
            ),
            rankDelta = 1L,
        ),
        ChallengeRank(
            challengeId = 7L,
            rank = 7L,
            artist = Artist(
                id = 7L,
                name = "윤가은",
                profileImageUrl = "",
                groupName = "에스파",
            ),
            song = ChallengeSong(
                title = "Whiplash",
                artistName = "에스파",
            ),
            rankDelta = 0L,
        ),
        ChallengeRank(
            challengeId = 8L,
            rank = 8L,
            artist = Artist(
                id = 8L,
                name = "오예린",
                profileImageUrl = "",
                groupName = "트와이스",
            ),
            song = ChallengeSong(
                title = "Strategy",
                artistName = "트와이스",
            ),
            rankDelta = -2L,
        ),
        ChallengeRank(
            challengeId = 9L,
            rank = 9L,
            artist = Artist(
                id = 9L,
                name = "강나연",
                profileImageUrl = "",
                groupName = "엔믹스",
            ),
            song = ChallengeSong(
                title = "Dash",
                artistName = "엔믹스",
            ),
            rankDelta = 4L,
        ),
        ChallengeRank(
            challengeId = 10L,
            rank = 10L,
            artist = Artist(
                id = 10L,
                name = "임수아",
                profileImageUrl = "",
                groupName = "스테이씨",
            ),
            song = ChallengeSong(
                title = "Bebe",
                artistName = "스테이씨",
            ),
            rankDelta = -6L,
        ),
        ChallengeRank(
            challengeId = 11L,
            rank = 11L,
            artist = Artist(
                id = 11L,
                name = "서다인",
                profileImageUrl = "",
                groupName = "있지",
            ),
            song = ChallengeSong(
                title = "Born to Be",
                artistName = "있지",
            ),
            rankDelta = 3L,
        ),
        ChallengeRank(
            challengeId = 12L,
            rank = 12L,
            artist = Artist(
                id = 12L,
                name = "노유정",
                profileImageUrl = "",
                groupName = "케플러",
            ),
            song = ChallengeSong(
                title = "Galileo",
                artistName = "케플러",
            ),
            rankDelta = 0L,
        ),
        ChallengeRank(
            challengeId = 13L,
            rank = 13L,
            artist = Artist(
                id = 13L,
                name = "백지호",
                profileImageUrl = "",
                groupName = "라이즈",
            ),
            song = ChallengeSong(
                title = "Boom Boom Bass",
                artistName = "라이즈",
            ),
            rankDelta = -4L,
        ),
        ChallengeRank(
            challengeId = 14L,
            rank = 14L,
            artist = Artist(
                id = 14L,
                name = "송하늘",
                profileImageUrl = "",
                groupName = "투어스",
            ),
            song = ChallengeSong(
                title = "Last Bell",
                artistName = "투어스",
            ),
            rankDelta = 7L,
        ),
        ChallengeRank(
            challengeId = 15L,
            rank = 15L,
            artist = Artist(
                id = 15L,
                name = "문채원",
                profileImageUrl = "",
                groupName = "베이비몬스터",
            ),
            song = ChallengeSong(
                title = "Drip",
                artistName = "베이비몬스터",
            ),
            rankDelta = -2L,
        ),
        ChallengeRank(
            challengeId = 16L,
            rank = 16L,
            artist = Artist(
                id = 16L,
                name = "구예린",
                profileImageUrl = "",
                groupName = "아일릿",
            ),
            song = ChallengeSong(
                title = "Magnetic",
                artistName = "아일릿",
            ),
            rankDelta = 1L,
        ),
        ChallengeRank(
            challengeId = 17L,
            rank = 17L,
            artist = Artist(
                id = 17L,
                name = "장미르",
                profileImageUrl = "",
                groupName = "제로베이스원",
            ),
            song = ChallengeSong(
                title = "Feel the Pop",
                artistName = "제로베이스원",
            ),
            rankDelta = 0L,
        ),
        ChallengeRank(
            challengeId = 18L,
            rank = 18L,
            artist = Artist(
                id = 18L,
                name = "홍시아",
                profileImageUrl = "",
                groupName = "우주소녀",
            ),
            song = ChallengeSong(
                title = "Last Sequence",
                artistName = "우주소녀",
            ),
            rankDelta = -5L,
        ),
        ChallengeRank(
            challengeId = 19L,
            rank = 19L,
            artist = Artist(
                id = 19L,
                name = "차은비",
                profileImageUrl = "",
                groupName = "프로미스나인",
            ),
            song = ChallengeSong(
                title = "Supersonic",
                artistName = "프로미스나인",
            ),
            rankDelta = 9L,
        ),
        ChallengeRank(
            challengeId = 20L,
            rank = 20L,
            artist = Artist(
                id = 20L,
                name = "권도희",
                profileImageUrl = "",
                groupName = "아이브",
            ),
            song = ChallengeSong(
                title = "Accendio",
                artistName = "아이브",
            ),
            rankDelta = -1L,
        ),
        ChallengeRank(
            challengeId = 21L,
            rank = 21L,
            artist = Artist(
                id = 21L,
                name = "신라온",
                profileImageUrl = "",
                groupName = "엔하이픈",
            ),
            song = ChallengeSong(
                title = "Bite Me",
                artistName = "엔하이픈",
            ),
            rankDelta = 2L,
        ),
        ChallengeRank(
            challengeId = 22L,
            rank = 22L,
            artist = Artist(
                id = 22L,
                name = "유하람",
                profileImageUrl = "",
                groupName = "르세라핌",
            ),
            song = ChallengeSong(
                title = "Smart",
                artistName = "르세라핌",
            ),
            rankDelta = 0L,
        ),
        ChallengeRank(
            challengeId = 23L,
            rank = 23L,
            artist = Artist(
                id = 23L,
                name = "명지우",
                profileImageUrl = "",
                groupName = "뉴진스",
            ),
            song = ChallengeSong(
                title = "Supernatural",
                artistName = "뉴진스",
            ),
            rankDelta = -8L,
        ),
        ChallengeRank(
            challengeId = 24L,
            rank = 24L,
            artist = Artist(
                id = 24L,
                name = "안소울",
                profileImageUrl = "",
                groupName = "에스파",
            ),
            song = ChallengeSong(
                title = "Supernova",
                artistName = "에스파",
            ),
            rankDelta = 6L,
        ),
        ChallengeRank(
            challengeId = 25L,
            rank = 25L,
            artist = Artist(
                id = 25L,
                name = "표하은",
                profileImageUrl = "",
                groupName = "트와이스",
            ),
            song = ChallengeSong(
                title = "One Spark",
                artistName = "트와이스",
            ),
            rankDelta = -3L,
        ),
        ChallengeRank(
            challengeId = 26L,
            rank = 26L,
            artist = Artist(
                id = 26L,
                name = "남기쁨",
                profileImageUrl = "",
                groupName = "엔믹스",
            ),
            song = ChallengeSong(
                title = "Love Me Like This",
                artistName = "엔믹스",
            ),
            rankDelta = 0L,
        ),
        ChallengeRank(
            challengeId = 27L,
            rank = 27L,
            artist = Artist(
                id = 27L,
                name = "도새롬",
                profileImageUrl = "",
                groupName = "스테이씨",
            ),
            song = ChallengeSong(
                title = "Teddy Bear",
                artistName = "스테이씨",
            ),
            rankDelta = 4L,
        ),
        ChallengeRank(
            challengeId = 28L,
            rank = 28L,
            artist = Artist(
                id = 28L,
                name = "황시윤",
                profileImageUrl = "",
                groupName = "투모로우바이투게더",
            ),
            song = ChallengeSong(
                title = "Deja Vu",
                artistName = "투모로우바이투게더",
            ),
            rankDelta = -7L,
        ),
        ChallengeRank(
            challengeId = 29L,
            rank = 29L,
            artist = Artist(
                id = 29L,
                name = "배로아",
                profileImageUrl = "",
                groupName = "있지",
            ),
            song = ChallengeSong(
                title = "Cake",
                artistName = "있지",
            ),
            rankDelta = 1L,
        ),
        ChallengeRank(
            challengeId = 30L,
            rank = 30L,
            artist = Artist(
                id = 30L,
                name = "천보름",
                profileImageUrl = "",
                groupName = "케플러",
            ),
            song = ChallengeSong(
                title = "Shooting Star",
                artistName = "케플러",
            ),
            rankDelta = 0L,
        ),
    )
}