package com.ssafy.motif.feature.mypage.impl.mypage.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import com.ssafy.motif.core.designsystem.component.LoadingWheel
import com.ssafy.motif.core.designsystem.component.Text
import com.ssafy.motif.core.designsystem.theme.MotifTheme
import com.ssafy.motif.core.model.mypage.UserProfile
import com.ssafy.motif.feature.mypage.impl.mypage.MyPageUiState
import com.ssafy.motif.core.designsystem.R as DesignSystemR

@Composable
internal fun MyProfileSection(
    profile: UserProfile,
    modifier: Modifier = Modifier,
){
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 20.dp),
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
    }
}