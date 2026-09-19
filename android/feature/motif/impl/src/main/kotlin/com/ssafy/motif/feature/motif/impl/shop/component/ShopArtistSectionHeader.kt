package com.ssafy.motif.feature.motif.impl.shop.component

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ssafy.motif.core.designsystem.component.Button
import com.ssafy.motif.core.designsystem.component.Icon
import com.ssafy.motif.core.designsystem.component.MotifButtonDefaults
import com.ssafy.motif.core.designsystem.component.Text
import com.ssafy.motif.core.designsystem.icon.MotifIcons
import com.ssafy.motif.core.designsystem.theme.MotifTheme
import com.ssafy.motif.core.model.home.Artist
import com.ssafy.motif.feature.motif.impl.R
import com.ssafy.motif.feature.motif.impl.shop.ShopPreviewFixtures

/**
 * 아티스트 섹션 헤더. 구독권 보유 여부에 따라 P 뱃지 또는 Pass 구매 버튼을 표시한다.
 */
@Composable
internal fun ShopArtistSectionHeader(
    artist: Artist,
    hasPass: Boolean,
    onPassClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = artist.profileImageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(AvatarSize)
                .clip(CircleShape),
        )

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = artist.name,
            color = MotifTheme.colors.typo,
            style = MotifTheme.typography.titleSB18,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(modifier = Modifier.width(8.dp))

        if (hasPass) {
            PassBadge()
        } else {
            PassPurchaseButton(onClick = onPassClick)
        }
    }
}

@Composable
private fun PassBadge(
    modifier: Modifier = Modifier,
) {
    // 아티스트 상세와 동일한 아이콘. 핑크 배경과 흰 P가 아이콘에 그려져 있어 틴트를 적용하지 않는다.
    Icon(
        painter = painterResource(MotifIcons.MotifPass),
        contentDescription = stringResource(R.string.feature_motif_impl_pass_owned),
        modifier = modifier.size(PassBadgeSize),
        tint = Color.Unspecified,
    )
}

@Composable
private fun PassPurchaseButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(percent = 50),
        colors = MotifButtonDefaults.buttonColors(
            containerColor = MotifTheme.colors.pink,
            contentColor = MotifTheme.colors.white,
        ),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = stringResource(R.string.feature_motif_impl_pass_purchase),
            style = MotifTheme.typography.captionSB12,
        )
    }
}

private val AvatarSize = 44.dp
private val PassBadgeSize = 24.dp

@Preview(name = "Pass owned", showBackground = true)
@Composable
private fun ShopArtistSectionHeaderOwnedPreview() {
    MotifTheme {
        ShopArtistSectionHeader(
            artist = ShopPreviewFixtures.artists().first(),
            hasPass = true,
            onPassClick = {},
        )
    }
}

@Preview(name = "Pass not owned", showBackground = true)
@Composable
private fun ShopArtistSectionHeaderNotOwnedPreview() {
    MotifTheme {
        ShopArtistSectionHeader(
            artist = ShopPreviewFixtures.artists()[1],
            hasPass = false,
            onPassClick = {},
        )
    }
}
