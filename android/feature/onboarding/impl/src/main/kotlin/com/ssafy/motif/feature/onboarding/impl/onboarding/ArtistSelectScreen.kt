package com.ssafy.motif.feature.onboarding.impl.onboarding

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import com.ssafy.motif.core.model.artist.Artist

@Composable
internal fun ArtistSelectScreen(
    uiState: OnboardingUiState,
    onArtistClick: (Long) -> Unit,
    onQueryChange: (String) -> Unit,
    onRemoveClick: () -> Unit,
    onNextClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        ArtistSearchBar(
            query = uiState.query,
            onQueryChange = onQueryChange,
            onRemoveClick = onRemoveClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = 20.dp,
                    top = 20.dp,
                    end = 20.dp,
                ),
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(20.dp),
            horizontalArrangement =
                Arrangement.spacedBy(20.dp),
            verticalArrangement =
                Arrangement.spacedBy(20.dp),
        ) {
            items(
                items = uiState.filteredArtists,
                key = { artist ->
                    artist.id
                },
            ) { artist ->
                ArtistCard(
                    artist = artist,
                    selected =
                        artist.id in uiState.selectedArtists,
                    onClick = {
                        onArtistClick(
                            artist.id,
                        )
                    },
                )
            }
        }

        if (
            uiState.query.isNotBlank() &&
            uiState.filteredArtists.isEmpty() &&
            !uiState.isSearching
        ) {
            Text(
                text = "검색 결과가 없습니다",
                style =
                    MotifTheme.typography.bodyR14,
                color =
                    MotifTheme.colors.gray400,
                modifier = Modifier
                    .align(
                        Alignment.CenterHorizontally,
                    )
                    .padding(20.dp),
            )
        }

        Button(
            onClick = onNextClick,
            enabled = uiState.canProceed,
            shape = RoundedCornerShape(5.dp),
            colors =
                MotifButtonDefaults.buttonColors(
                    containerColor =
                        MotifTheme.colors.pink,
                    contentColor =
                        MotifTheme.colors.typo,
                ),
            modifier = Modifier
                .padding(
                    start = 20.dp,
                    end = 20.dp,
                    top = 8.dp,
                    bottom = 24.dp
                )
                .fillMaxWidth()
                .height(54.dp),
        ) {
            Text(
                text =
                    "${uiState.selectedArtists.size}명 선택",
                style =
                    MotifTheme.typography.titleSB18,
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
        color = MotifTheme.colors.black,
        contentPadding =
            PaddingValues(
                horizontal = 7.dp,
                vertical = 12.dp,
            ),
    ) {
        Row(
            verticalAlignment =
                Alignment.CenterVertically,
        ) {
            Icon(
                painter =
                    painterResource(
                        MotifIcons.Search,
                    ),
                contentDescription = null,
                tint =
                    MotifTheme.colors.typo,
                modifier =
                    Modifier.size(20.dp),
            )

            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle =
                    MotifTheme.typography.bodyR14
                        .copy(
                            color =
                                MotifTheme.colors.typo,
                        ),
                cursorBrush =
                    SolidColor(
                        MotifTheme.colors.typo,
                    ),
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
                decorationBox = { innerTextField ->
                    if (query.isEmpty()) {
                        Text(
                            text =
                                "함께하고 싶은 멤버나 그룹을 찾아보세요",
                            style =
                                MotifTheme.typography.bodyR14,
                            color =
                                MotifTheme.colors.typo,
                        )
                    }

                    innerTextField()
                },
            )

            IconButton(
                painter =
                    painterResource(
                        MotifIcons.Close,
                    ),
                onClick = onRemoveClick,
                colors =
                    ModifIconButtonDefaults
                        .iconButtonColors(
                            contentColor =
                                MotifTheme.colors.typo,
                        ),
                modifier = Modifier
                    .size(20.dp)
                    .padding(end = 5.dp),
            )
        }
    }
}

@Composable
internal fun ArtistCard(
    artist: Artist,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier.clickable(
                onClick = onClick,
            ),
        horizontalAlignment =
            Alignment.CenterHorizontally,
    ) {
        AsyncImage(
            model = artist.profileImageUrl,
            contentDescription = artist.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .border(
                    width = 3.dp,
                    color =
                        if (selected) {
                            MotifTheme.colors.pink
                        } else {
                            Color.Transparent
                        },
                    shape = CircleShape,
                )
                .padding(3.dp)
                .clip(CircleShape),
        )

        Text(
            text = artist.name,
            style =
                MotifTheme.typography.bodySB16,
            color =
                MotifTheme.colors.typo,
            maxLines = 1,
            overflow =
                TextOverflow.Visible,
            modifier =
                Modifier.padding(top = 8.dp),
        )

        Text(
            text = artist.groupName.orEmpty(),
            style =
                MotifTheme.typography.captionSB12,
            color =
                MotifTheme.colors.typo,
            maxLines = 1,
            overflow =
                TextOverflow.Visible,
        )
    }
}