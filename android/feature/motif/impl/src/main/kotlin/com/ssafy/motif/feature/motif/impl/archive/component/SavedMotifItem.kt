package com.ssafy.motif.feature.motif.impl.archive.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ssafy.motif.core.designsystem.component.IconButton
import com.ssafy.motif.core.designsystem.component.ModifIconButtonDefaults
import com.ssafy.motif.core.designsystem.component.Text
import com.ssafy.motif.core.designsystem.icon.MotifIcons
import com.ssafy.motif.core.designsystem.theme.MotifTheme
import com.ssafy.motif.core.model.motif.SavedMotif
import com.ssafy.motif.core.ui.formatter.formatDotDate
import com.ssafy.motif.feature.motif.impl.R
import com.ssafy.motif.feature.motif.impl.archive.ArchiveMockData

/**
 * 보관함 목록의 저장 모티프 한 항목. 썸네일 + 저장일 + 설명 + 대표 모티프 별 토글.
 *
 * @param onClick 항목 선택 → 상단 재생 대상 변경.
 * @param onRepresentativeClick 별 아이콘 탭 → 대표 모티프 설정/해제. 항목 클릭과는 별개로 전달된다.
 * @param isRepresentativeUpdating 대표 설정/해제 처리 중인지. true면 별을 비활성화해 중복 탭을 막는다.
 */
@Composable
internal fun SavedMotifItem(
    motif: SavedMotif,
    onClick: (Long) -> Unit,
    onRepresentativeClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    isRepresentativeUpdating: Boolean = false,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(role = Role.Button) { onClick(motif.id) },
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .width(ThumbnailWidth)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(ThumbnailCornerRadius)),
        ) {
            AsyncImage(
                model = motif.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )

            RepresentativeToggle(
                isRepresentative = motif.isRepresentative,
                enabled = !isRepresentativeUpdating,
                onClick = { onRepresentativeClick(motif.id) },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(ToggleEdgePadding),
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = motif.savedAt.formatDotDate(),
                color = MotifTheme.colors.gray500,
                style = MotifTheme.typography.captionR12,
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = motif.description,
                color = MotifTheme.colors.typo,
                style = MotifTheme.typography.bodyR14,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * 좋아요 토글(ArtistChallengeOverlay)과 같은 방식: 설정이면 채워진 아이콘 + 핑크, 아니면 빈 아이콘 + 흰색.
 */
@Composable
private fun RepresentativeToggle(
    isRepresentative: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onClick,
        painter = painterResource(
            if (isRepresentative) MotifIcons.FilledHeart else MotifIcons.Heart,
        ),
        contentDescription = stringResource(
            if (isRepresentative) {
                R.string.feature_motif_impl_representative_clear
            } else {
                R.string.feature_motif_impl_representative_set
            },
        ),
        modifier = modifier,
        enabled = enabled,
        size = ToggleSize,
        padding = PaddingValues(ToggleInnerPadding),
        colors = ModifIconButtonDefaults.iconButtonColors(
            contentColor = if (isRepresentative) MotifTheme.colors.pink else MotifTheme.colors.white,
        ),
    )
}

private val ThumbnailWidth = 178.dp
private val ThumbnailCornerRadius = 8.dp

// 썸네일(178×100dp) 안에 들어가야 하므로 목록 밖 버튼보다 작게 잡는다.
private val ToggleSize = 40.dp
private val ToggleInnerPadding = 6.dp
private val ToggleEdgePadding = 4.dp

@Preview(name = "Representative", showBackground = true)
@Composable
private fun SavedMotifItemRepresentativePreview() {
    MotifTheme {
        SavedMotifItem(
            motif = ArchiveMockData.savedMotifs[0],
            onClick = {},
            onRepresentativeClick = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SavedMotifItemPreview() {
    MotifTheme {
        SavedMotifItem(
            motif = ArchiveMockData.savedMotifs[1],
            onClick = {},
            onRepresentativeClick = {},
        )
    }
}
