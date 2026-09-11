package com.ssafy.motif.feature.onboarding.impl.artistselect

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.ssafy.motif.core.designsystem.component.Button
import com.ssafy.motif.core.designsystem.component.Icon
import com.ssafy.motif.core.designsystem.component.IconButton
import com.ssafy.motif.core.designsystem.component.ModifIconButtonDefaults
import com.ssafy.motif.core.designsystem.component.MotifButtonDefaults
import com.ssafy.motif.core.designsystem.component.Surface
import com.ssafy.motif.core.designsystem.component.Text
import com.ssafy.motif.core.designsystem.icon.MotifIcons
import com.ssafy.motif.core.designsystem.theme.MotifTheme

@Composable
fun ArtistSelectRoute(
    onNextClick: (List<Long>) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ArtistSelectViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ArtistSelectScreen(
        uiState = uiState,
        onArtistClick = viewModel::toggleArtist,
        onQueryChange = viewModel::updateQuery,
        onRemoveClick = viewModel::removeQuery,
        onNextClick = {onNextClick(uiState.selectedArtists.toList())},
        modifier = modifier,
    )
}


@Composable
internal fun ArtistSelectScreen(
    uiState: ArtistSelectUiState,
    onArtistClick: (Long) -> Unit,   //아티스트 클릭시
    onQueryChange: (String) -> Unit,
    onRemoveClick: () -> Unit,
    onNextClick: () -> Unit,         //n명 선택 버튼 클릭 시
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {

        ArtistSearchBar(
            query = uiState.query,
            onQueryChange = onQueryChange,
            onRemoveClick = onRemoveClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, top = 20.dp, end = 20.dp),
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(20.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),    //사이 좌우간격
            verticalArrangement = Arrangement.spacedBy(20.dp)       //사이 상하간격
        )

        {
            items(items = uiState.filteredArtists, key = { it.id }) { artist ->
                ArtistCard(artist = artist,
                    selected = artist.id in uiState.selectedArtists,
                    onClick = {onArtistClick(artist.id)},
                )
            }

        }

        if(uiState.query.isNotBlank() && uiState.filteredArtists.isEmpty()){
            Text(
                text = "검색 결과가 없습니다",
                style = MotifTheme.typography.bodyR14,
                color = MotifTheme.colors.gray400,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(20.dp),
            )
        }

        Button(
            onClick = onNextClick,
            enabled = uiState.canProceed,
            shape = RoundedCornerShape(5.dp),
            colors = MotifButtonDefaults.buttonColors(
                containerColor = MotifTheme.colors.pink,
                contentColor = MotifTheme.colors.typo,
            ),
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 24.dp)
                .fillMaxWidth()
                .height(54.dp),
        ) {
            Text(
                text = "${uiState.selectedArtists.size}명 선택",
                style = MotifTheme.typography.titleSB18,
            )
        }
    }
}

internal object ArtistMokData {

    private val samples = listOf(
        "이새롬" to "프로미스나인",
        "송하영" to "프로미스나인",
        "노지선" to "프로미스나인",
        "이서연" to "프로미스나인",
        "이채영" to "프로미스나인",
        "이나경" to "프로미스나인",
        "백지헌" to "프로미스나인",
        "박지원" to "프로미스나인",
        "장규리" to "프로미스나인",
        "안유진" to "IVE",
        "가을" to "IVE",
        "레이" to "IVE",
        "장원영" to "IVE",
        "리즈" to "IVE",
        "이서" to "IVE",
        "민지" to "뉴진스",
        "하니" to "뉴진스",
        "다니엘" to "뉴진스",
        "해린" to "뉴진스",
        "혜인" to "뉴진스",
        "김채원" to "르세라핌",
        "사쿠라" to "르세라핌",
        "허윤진" to "르세라핌",
        "카즈하" to "르세라핌",
        "홍은채" to "르세라핌",
        "예지" to "ITZY",
        "리아" to "ITZY",
        "류진" to "ITZY",
        "채령" to "ITZY",
        "유나" to "ITZY",
    )
    val artists: List<ArtistUiModel> = samples.mapIndexed { index, (name, groupName) ->
        ArtistUiModel(
            id = index.toLong(),
            name = name,
            groupName = groupName,
            profileImageUrl = null,
        )
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
                    if(query.isEmpty()){
                        Text(
                            text = "함께하고 싶은 멤버나 그룹을 찾아보세요",
                            style = MotifTheme.typography.bodyR14,
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

@Composable
fun ArtistCard(
    artist: ArtistUiModel,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AsyncImage(
            model = artist.profileImageUrl,
            contentDescription = artist.name,
            contentScale = ContentScale.Crop,
            placeholder =
                painterResource(com.ssafy.motif.core.designsystem.R.drawable.img_profile),
            error =
                painterResource(com.ssafy.motif.core.designsystem.R.drawable.img_profile),
            fallback =
                painterResource(com.ssafy.motif.core.designsystem.R.drawable.img_profile),
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .border(
                    width = 3.dp,
                    color = if (selected) MotifTheme.colors.pink
                    else Color.Transparent,
                    shape = CircleShape,
                )
                .padding(3.dp)
                .clip(CircleShape),
        )
        Text(
            text = artist.name,
            style = MotifTheme.typography.bodySB16,
            color = MotifTheme.colors.typo,
            maxLines = 1,
            overflow = TextOverflow.Visible,
            modifier = Modifier.padding(top = 8.dp),
        )

        Text(
            text = artist.groupName,
            style = MotifTheme.typography.captionSB12,
            color = MotifTheme.colors.typo,
            maxLines = 1,
            overflow = TextOverflow.Visible,
        )
    }
}
