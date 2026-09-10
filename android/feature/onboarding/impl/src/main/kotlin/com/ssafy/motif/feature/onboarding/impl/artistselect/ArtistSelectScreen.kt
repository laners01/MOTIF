package com.ssafy.motif.feature.onboarding.impl.artistselect

import android.widget.Toast
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.ssafy.motif.core.designsystem.component.Button
import com.ssafy.motif.core.designsystem.component.MotifButtonDefaults
import com.ssafy.motif.core.designsystem.component.Text
import com.ssafy.motif.core.designsystem.theme.MotifTheme

@Composable
fun ArtistSelectRoute(
    modifier: Modifier = Modifier,
    viewModel: ArtistSelectViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    ArtistSelectScreen(
        uiState = uiState,
        onArtistClick = viewModel::toggleArtist,
        onNextClick = {  Toast.makeText(
            context,
            "${uiState.selectedArtists.size}명 선택 완료",
            Toast.LENGTH_SHORT
        ).show() },
        modifier = modifier,
    )
}


@Composable
internal fun ArtistSelectScreen(
    uiState: ArtistSelectUiState,
    onArtistClick: (Long) -> Unit,   //아티스트 클릭시
    onNextClick: () -> Unit,         //n명 선택 버튼 클릭 시
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = modifier.weight(1f),
            contentPadding = PaddingValues(20.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),    //사이 좌우간격
            verticalArrangement = Arrangement.spacedBy(20.dp)       //사이 상하간격
        ) {
            items(items = uiState.artists, key = { it.id }) { artist ->
                ArtistCard(artist = artist,
                    selected = artist.id in uiState.selectedArtists,
                    onClick = {onArtistClick(artist.id)},
                )
            }

        }
        Button(
            onClick = onNextClick,
            enabled = uiState.canProceed,
            shape = RoundedCornerShape(5.dp),
            colors = MotifButtonDefaults.buttonColors(
                containerColor = MotifTheme.colors.pink,
                contentColor = MotifTheme.colors.white,
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

    private val sample = ArtistUiModel(
        0L,
        "이채영",
        "프로미스나인",
        null
    )
    val artists: List<ArtistUiModel> = List(30) { index ->
        sample.copy(id = index.toLong())
    }
    val selectedIds: Set<Long> = setOf(0L,2L,5L)
}


@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun ArtistSelectScreenPreview() {
    MotifTheme {
        ArtistSelectScreen(
            uiState = ArtistSelectUiState(
                artists = ArtistMokData.artists,
                selectedArtists = ArtistMokData.selectedIds,
            ),
            onArtistClick = {},
            onNextClick = {},
        )
    }
}

@Composable
private fun ArtistCard(
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
            color = MotifTheme.colors.white,
            maxLines = 1,
            overflow = TextOverflow.Visible,
            modifier = Modifier.padding(top = 8.dp),
        )

        Text(
            text = artist.groupName,
            style = MotifTheme.typography.captionSB12,
            color = MotifTheme.colors.white,
            maxLines = 1,
            overflow = TextOverflow.Visible,
        )
    }
}
