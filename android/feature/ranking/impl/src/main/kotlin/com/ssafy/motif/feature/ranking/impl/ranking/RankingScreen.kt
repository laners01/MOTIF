package com.ssafy.motif.feature.ranking.impl.ranking

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import coil.compose.AsyncImage
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.motif.core.designsystem.component.Icon
import com.ssafy.motif.core.designsystem.component.IconButton
import com.ssafy.motif.core.designsystem.component.ModifIconButtonDefaults
import com.ssafy.motif.core.designsystem.component.Surface
import com.ssafy.motif.core.designsystem.component.Text
import com.ssafy.motif.core.designsystem.icon.MotifIcons
import com.ssafy.motif.core.designsystem.theme.MotifTheme


@Composable
fun RankingRoute(
    modifier: Modifier = Modifier,
    viewModel: RankingViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    RankingScreen(
        uiState = uiState,
        onArtistClick = viewModel::showArtistDetail,
        onQueryChange = viewModel::updateQuery,
        onRemoveClick = viewModel::removeQuery,
        onPlayClick = viewModel::playChallenge,
        modifier = modifier,
    )
}

@Composable
internal fun RankingScreen(
    uiState: RankingUiState,
    onPlayClick: (Long) -> Unit,    //재생버튼 클릭시
    onArtistClick:(Long) -> Unit,   //아티스트 클릭시
    onQueryChange:(String) -> Unit, //검색어 변경시
    onRemoveClick:() -> Unit,       //검색어 삭제
    modifier: Modifier = Modifier,
){
    Column(modifier = modifier.fillMaxSize(),){
        Row(modifier = Modifier.padding(start = 10.dp, top = 20.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                painter = painterResource(MotifIcons.ArrowLeft),
                onClick = onRemoveClick,
                size = 48.dp,
                colors = ModifIconButtonDefaults.iconButtonColors(
                    contentColor = MotifTheme.colors.white
                ),
                modifier = Modifier
                    .padding(end = 5.dp)
            )
            Text(
                text = "Who's on",
                style = MotifTheme.typography.titleSB20,
                color = MotifTheme.colors.white,
                maxLines = 1,
                overflow = TextOverflow.Visible,
            )

        }

        ArtistSearchBar(
            query = uiState.query,
            onQueryChange = onQueryChange,
            onRemoveClick = onRemoveClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, top = 10.dp, end = 20.dp),
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(1),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(20.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),    //사이 좌우간격
            verticalArrangement = Arrangement.spacedBy(20.dp)       //사이 상하간격
        )

        {
            items(items = uiState.ranking, key = { it.id }) { challenge ->
                ArtistRankCard(challenge = challenge,
                    onPlayClick = {onPlayClick(challenge.id)},
                    onArtistClick = {onArtistClick(challenge.id)},
                )
            }

        }
    }



}

@Composable
fun ArtistRankCard(
    challenge: ChallengeUiModel,
    onPlayClick: () -> Unit,
    onArtistClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "${challenge.ranking}",
                style = MotifTheme.typography.titleSB20,
                color = MotifTheme.colors.white,
                maxLines = 1,
                overflow = TextOverflow.Visible,
                modifier = Modifier.padding(bottom = 3.dp)
            )
            Row(verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 3.dp)){
                Text(
                    text = if(challenge.rankingChanged>0){
                        "▲"
                    }else if(challenge.rankingChanged<0){
                        "▼"
                    }else{
                        "-"
                    },

                    style = MotifTheme.typography.captionR10,
                    color = if(challenge.rankingChanged>0){
                        MotifTheme.colors.pink
                    }else if(challenge.rankingChanged<0){
                        MotifTheme.colors.gray500
                    }else{
                        MotifTheme.colors.gray500
                    },

                    maxLines = 1,
                    overflow = TextOverflow.Visible,
                )

                Text(
                    text = if(challenge.rankingChanged.toInt() == 0){
                        ""
                    }else if(challenge.rankingChanged<0){
                        "${challenge.rankingChanged}".drop(1)
                    }else{
                        "${challenge.rankingChanged}"
                    },
                    style = MotifTheme.typography.captionR10,
                    color = MotifTheme.colors.white,
                    maxLines = 1,
                    overflow = TextOverflow.Visible,
                )


            }


        }


        AsyncImage(
            model = challenge.profileImageUrl,
            contentDescription = challenge.name,
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
                .clip(RoundedCornerShape(5.dp))
                .clickable(onClick = onArtistClick),
        )
        Row(modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
            ) {
            Text(
                text = challenge.name,
                style = MotifTheme.typography.bodySB16,
                color = MotifTheme.colors.white,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(end = 5.dp)
            )

            Text(
                text = challenge.groupName,
                style = MotifTheme.typography.captionSB10,
                color = MotifTheme.colors.gray500,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

        }
        Text(
            text = challenge.challengeName,
            style = MotifTheme.typography.bodySB14,
            color = MotifTheme.colors.white,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Box(
            modifier = Modifier
                .size(30.dp)
                .clickable(onClick = onPlayClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(MotifIcons.FilledPlay),
                contentDescription = null,
                tint = MotifTheme.colors.pink,
                modifier = Modifier.size(28.dp),
            )
            Icon(
                painter = painterResource(MotifIcons.FilledPlay),
                contentDescription = "재생",
                tint = MotifTheme.colors.black,
                modifier = Modifier.size(22.dp)
                    .padding(end = 1.dp),
            )
        }


    }
}


@Composable
private fun ArtistSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onRemoveClick:() -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = MotifTheme.colors.gray700,
        contentPadding = PaddingValues(horizontal = 7.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically){
            Icon(
                painter = painterResource(MotifIcons.Search),
                null,
                tint = MotifTheme.colors.white,
                modifier = Modifier.size(20.dp)
            )

            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MotifTheme.typography.bodyR14.copy(
                    color = MotifTheme.colors.white,
                ),
                cursorBrush = SolidColor(MotifTheme.colors.white),
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
                decorationBox = { innerTextField ->
                    if(query.isEmpty()){
                        Text(
                            text = "함께하고 싶은 멤버나 그룹을 찾아보세요",
                            style = MotifTheme.typography.bodyR14,
                            color = MotifTheme.colors.white,
                        )
                    }
                    innerTextField()
                },
            )
            IconButton(
                painter = painterResource(MotifIcons.Close),
                onClick = onRemoveClick,
                colors = ModifIconButtonDefaults.iconButtonColors(
                    contentColor = MotifTheme.colors.white
                ),
                modifier = Modifier
                    .size(20.dp)
                    .padding(end = 5.dp)
            )

        }
    }
}
internal object RankingMokData {

    /** rankingChanged: 양수는 순위 상승, 음수는 하락, 0은 유지를 뜻한다. */
    val ranking: List<ChallengeUiModel> = listOf(
        ChallengeUiModel(
            id = 1L,
            name = "이채영",
            groupName = "프로미스나인",
            profileImageUrl = null,
            ranking = 1L,
            challengeName = "Supersonic 챌린지",
            rankingChanged = 2L,
        ),
        ChallengeUiModel(
            id = 2L,
            name = "박지원",
            groupName = "프로미스나인",
            profileImageUrl = null,
            ranking = 2L,
            challengeName = "Supersonic 챌린지",
            rankingChanged = -1L,
        ),
        ChallengeUiModel(
            id = 3L,
            name = "김민주",
            groupName = "아이브",
            profileImageUrl = null,
            ranking = 3L,
            challengeName = "Rebel Heart 챌린지",
            rankingChanged = 0L,
        ),
        ChallengeUiModel(
            id = 4L,
            name = "정하윤",
            groupName = "르세라핌",
            profileImageUrl = null,
            ranking = 4L,
            challengeName = "Crazy 챌린지",
            rankingChanged = 5L,
        ),
        ChallengeUiModel(
            id = 5L,
            name = "최서연",
            groupName = "뉴진스",
            profileImageUrl = null,
            ranking = 5L,
            challengeName = "How Sweet 챌린지",
            rankingChanged = -3L,
        ),
        ChallengeUiModel(
            id = 6L,
            name = "한소희",
            groupName = "아이들",
            profileImageUrl = null,
            ranking = 6L,
            challengeName = "Klaxon 챌린지",
            rankingChanged = 1L,
        ),
        ChallengeUiModel(
            id = 7L,
            name = "윤가은",
            groupName = "에스파",
            profileImageUrl = null,
            ranking = 7L,
            challengeName = "Whiplash 챌린지",
            rankingChanged = 0L,
        ),
        ChallengeUiModel(
            id = 8L,
            name = "오예린",
            groupName = "트와이스",
            profileImageUrl = null,
            ranking = 8L,
            challengeName = "Strategy 챌린지",
            rankingChanged = -2L,
        ),
        ChallengeUiModel(
            id = 9L,
            name = "강나연",
            groupName = "엔믹스",
            profileImageUrl = null,
            ranking = 9L,
            challengeName = "Dash 챌린지",
            rankingChanged = 4L,
        ),
        ChallengeUiModel(
            id = 10L,
            name = "임수아",
            groupName = "스테이씨",
            profileImageUrl = null,
            ranking = 10L,
            challengeName = "Bebe 챌린지",
            rankingChanged = -6L,
        ),
        ChallengeUiModel(
            id = 11L,
            name = "서다인",
            groupName = "있지",
            profileImageUrl = null,
            ranking = 11L,
            challengeName = "Born to Be 챌린지",
            rankingChanged = 3L,
        ),
        ChallengeUiModel(
            id = 12L,
            name = "노유정",
            groupName = "케플러",
            profileImageUrl = null,
            ranking = 12L,
            challengeName = "Galileo 챌린지",
            rankingChanged = 0L,
        ),
        ChallengeUiModel(
            id = 13L,
            name = "백지호",
            groupName = "라이즈",
            profileImageUrl = null,
            ranking = 13L,
            challengeName = "Boom Boom Bass 챌린지",
            rankingChanged = -4L,
        ),
        ChallengeUiModel(
            id = 14L,
            name = "송하늘",
            groupName = "투어스",
            profileImageUrl = null,
            ranking = 14L,
            challengeName = "Last Bell 챌린지",
            rankingChanged = 7L,
        ),
        ChallengeUiModel(
            id = 15L,
            name = "문채원",
            groupName = "베이비몬스터",
            profileImageUrl = null,
            ranking = 15L,
            challengeName = "Drip 챌린지",
            rankingChanged = -2L,
        ),
        ChallengeUiModel(
            id = 16L,
            name = "구예린",
            groupName = "아일릿",
            profileImageUrl = null,
            ranking = 16L,
            challengeName = "Magnetic 챌린지",
            rankingChanged = 1L,
        ),
        ChallengeUiModel(
            id = 17L,
            name = "장미르",
            groupName = "제로베이스원",
            profileImageUrl = null,
            ranking = 17L,
            challengeName = "Feel the Pop 챌린지",
            rankingChanged = 0L,
        ),
        ChallengeUiModel(
            id = 18L,
            name = "홍시아",
            groupName = "우주소녀",
            profileImageUrl = null,
            ranking = 18L,
            challengeName = "Last Sequence 챌린지",
            rankingChanged = -5L,
        ),
        ChallengeUiModel(
            id = 19L,
            name = "차은비",
            groupName = "프로미스나인",
            profileImageUrl = null,
            ranking = 19L,
            challengeName = "Supersonic 챌린지",
            rankingChanged = 9L,
        ),
        ChallengeUiModel(
            id = 20L,
            name = "권도희",
            groupName = "아이브",
            profileImageUrl = null,
            ranking = 20L,
            challengeName = "Accendio 챌린지",
            rankingChanged = -1L,
        ),
        ChallengeUiModel(
            id = 21L,
            name = "신라온",
            groupName = "엔하이픈",
            profileImageUrl = null,
            ranking = 21L,
            challengeName = "Bite Me 챌린지",
            rankingChanged = 2L,
        ),
        ChallengeUiModel(
            id = 22L,
            name = "유하람",
            groupName = "르세라핌",
            profileImageUrl = null,
            ranking = 22L,
            challengeName = "Smart 챌린지",
            rankingChanged = 0L,
        ),
        ChallengeUiModel(
            id = 23L,
            name = "명지우",
            groupName = "뉴진스",
            profileImageUrl = null,
            ranking = 23L,
            challengeName = "Supernatural 챌린지",
            rankingChanged = -8L,
        ),
        ChallengeUiModel(
            id = 24L,
            name = "안소울",
            groupName = "에스파",
            profileImageUrl = null,
            ranking = 24L,
            challengeName = "Supernova 챌린지",
            rankingChanged = 6L,
        ),
        ChallengeUiModel(
            id = 25L,
            name = "표하은",
            groupName = "트와이스",
            profileImageUrl = null,
            ranking = 25L,
            challengeName = "One Spark 챌린지",
            rankingChanged = -3L,
        ),
        ChallengeUiModel(
            id = 26L,
            name = "남기쁨",
            groupName = "엔믹스",
            profileImageUrl = null,
            ranking = 26L,
            challengeName = "Love Me Like This 챌린지",
            rankingChanged = 0L,
        ),
        ChallengeUiModel(
            id = 27L,
            name = "도새롬",
            groupName = "스테이씨",
            profileImageUrl = null,
            ranking = 27L,
            challengeName = "Teddy Bear 챌린지",
            rankingChanged = 4L,
        ),
        ChallengeUiModel(
            id = 28L,
            name = "황시윤",
            groupName = "투모로우바이투게더",
            profileImageUrl = null,
            ranking = 28L,
            challengeName = "Deja Vu 챌린지",
            rankingChanged = -7L,
        ),
        ChallengeUiModel(
            id = 29L,
            name = "배로아",
            groupName = "있지",
            profileImageUrl = null,
            ranking = 29L,
            challengeName = "Cake 챌린지",
            rankingChanged = 1L,
        ),
        ChallengeUiModel(
            id = 30L,
            name = "천보름",
            groupName = "케플러",
            profileImageUrl = null,
            ranking = 30L,
            challengeName = "Shooting Star 챌린지",
            rankingChanged = 0L,
        ),
    )

    val uiState: RankingUiState = RankingUiState(ranking = ranking)
}
