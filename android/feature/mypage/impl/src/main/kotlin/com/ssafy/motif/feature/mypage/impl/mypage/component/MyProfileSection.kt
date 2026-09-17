package com.ssafy.motif.feature.mypage.impl.mypage.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ssafy.motif.core.designsystem.component.IconButton
import com.ssafy.motif.core.designsystem.component.ModifIconButtonDefaults
import com.ssafy.motif.core.designsystem.component.Text
import com.ssafy.motif.core.designsystem.icon.MotifIcons
import com.ssafy.motif.core.designsystem.theme.MotifTheme
import com.ssafy.motif.core.model.mypage.UserProfile
import com.ssafy.motif.core.designsystem.R as DesignSystemR

@Composable
internal fun MyProfileSection(
    profile: UserProfile,
    onNotificationClick:() -> Unit,
    onSettingsClick:() -> Unit,
    modifier: Modifier = Modifier,
){
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 23.dp, top = 20.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ){
        AsyncImage(
            model = profile.profileImageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            placeholder = painterResource(DesignSystemR.drawable.profile_square),
            error = painterResource(DesignSystemR.drawable.profile_square),
            fallback = painterResource(DesignSystemR.drawable.profile_square),
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape),
        )
        Text(
            text = profile.nickname,
            style = MotifTheme.typography.bodyR16,
            color = MotifTheme.colors.typo,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.weight(1f))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            IconButton(
                painter = painterResource(MotifIcons.Bell),
                onClick = onNotificationClick,
                size = 24.dp,
                colors = ModifIconButtonDefaults.iconButtonColors(
                    contentColor = MotifTheme.colors.typo,
                ),
            )

            IconButton(
                painter = painterResource(MotifIcons.Settings),
                onClick = onSettingsClick,
                size = 24.dp,
                colors = ModifIconButtonDefaults.iconButtonColors(
                    contentColor = MotifTheme.colors.typo,
                ),
            )
        }
    }
}