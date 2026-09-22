package com.ssafy.motif.feature.mypage.impl.mypage.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ssafy.motif.core.designsystem.component.Button
import com.ssafy.motif.core.designsystem.component.GlassSurface
import com.ssafy.motif.core.designsystem.component.Icon
import com.ssafy.motif.core.designsystem.component.MotifButtonDefaults
import com.ssafy.motif.core.designsystem.component.Text
import com.ssafy.motif.core.designsystem.glass.glassBackdrop
import com.ssafy.motif.core.designsystem.glass.rememberGlassBackdropState
import com.ssafy.motif.core.designsystem.icon.MotifIcons
import com.ssafy.motif.core.designsystem.theme.MotifTheme
import com.ssafy.motif.core.model.artist.ArtistSubscription
import com.ssafy.motif.core.designsystem.R as DesignSystemR

@Composable
internal fun SubscribedArtistCard(
    artist: ArtistSubscription,
    isFlipped: Boolean,
    onClick: () -> Unit,
    onSeeMoreClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rotation by animateFloatAsState(
        targetValue = if (isFlipped) 180f else 0f,
        animationSpec = tween(durationMillis = 400),
        label = "artist card flip",
    )

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .graphicsLayer {
                rotationY = rotation
                cameraDistance = 12f * density
            }
            .clickable(onClick = onClick),
    ) {
        if (rotation <= 90f) {
            ArtistCardFront(artist = artist)
        } else {
            // 부모가 180도 돌아간 상태라, 뒤집지 않으면 내용이 좌우 반전된다.
            Box(modifier = Modifier.graphicsLayer { rotationY = 180f }) {
                ArtistCardBack(
                    artist = artist,
                    onSeeMoreClick = onSeeMoreClick,
                )
            }
        }
    }
}

@Composable
private fun ArtistCardFront(
    artist: ArtistSubscription,
    modifier: Modifier = Modifier,
) {
    ArtistProfileImage(
        artist = artist,
        modifier = modifier.fillMaxSize(),
    )
}

@Composable
private fun ArtistCardBack(
    artist: ArtistSubscription,
    onSeeMoreClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val backdropState = rememberGlassBackdropState()

    Box(modifier = modifier.fillMaxSize()) {
        ArtistProfileImage(
            artist = artist,
            modifier = Modifier
                .matchParentSize()
                .glassBackdrop(backdropState),
        )

        GlassSurface(
            backdropState = backdropState,
            modifier = Modifier.matchParentSize(),
            shape = RectangleShape,
            blurRadius = 16.dp,
            blurAlpha = 1f,
            tintColor = Color.Black.copy(alpha = 0.65f),
            borderWidth = 0.dp,
        ) {}

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "WITH ${artist.artist.name}",
                    style = MotifTheme.typography.bodySB16,
                    color = MotifTheme.colors.typo,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )

                if (artist.hasMotifPass) {
                    Icon(
                        painter = painterResource(MotifIcons.MotifPass),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            ArtistStatRow(
                label = "DAYS",
                value = "${artist.subscribedDays}",
            )

            ArtistStatRow(
                label = "Challenges",
                value = "${artist.challengeCount}",
            )

            ArtistStatRow(
                label = "MOTIF",
                value = "${artist.ownedMotifCount} / ${artist.totalMotifCount}",
            )

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = onSeeMoreClick,
                shape = RoundedCornerShape(50),
                colors = MotifButtonDefaults.buttonColors(
                    containerColor = MotifTheme.colors.pink,
                    contentColor = MotifTheme.colors.typo,
                ),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .height(28.dp),
            ) {
                Text(
                    text = "See More",
                    style = MotifTheme.typography.bodySB12,
                )
            }
        }
    }
}

@Composable
private fun ArtistStatRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MotifTheme.typography.bodyR12,
            color = MotifTheme.colors.typo,
        )

        Text(
            text = value,
            style = MotifTheme.typography.bodySB12,
            color = MotifTheme.colors.typo,
        )
    }
}

@Composable
private fun ArtistProfileImage(
    artist: ArtistSubscription,
    modifier: Modifier = Modifier,
) {
    AsyncImage(
        model = artist.artist.profileImageUrl,
        contentDescription = artist.artist.name,
        contentScale = ContentScale.Crop,
        placeholder = painterResource(DesignSystemR.drawable.profile_square),
        error = painterResource(DesignSystemR.drawable.profile_square),
        fallback = painterResource(DesignSystemR.drawable.profile_square),
        modifier = modifier,
    )
}
