package com.ssafy.motif.feature.artistdetail.impl.artistdetail.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ssafy.motif.core.designsystem.component.Button
import com.ssafy.motif.core.designsystem.component.Icon
import com.ssafy.motif.core.designsystem.component.MotifButtonDefaults
import com.ssafy.motif.core.designsystem.component.Surface
import com.ssafy.motif.core.designsystem.component.Text
import com.ssafy.motif.core.designsystem.icon.MotifIcons
import com.ssafy.motif.core.designsystem.theme.MotifTheme
import com.ssafy.motif.core.model.artistdetail.ArtistDetail

@Composable
internal fun ArtistDetailCard(
    artistDetail: ArtistDetail,
    isAlarmOn: Boolean,
    isSubscribed:Boolean,
    onSubscribeClick: () -> Unit,    //구독버튼 클릭시
    onAlarmClick:() -> Unit,        //알람버튼 클릭시
    modifier: Modifier = Modifier
){
    Column(modifier = modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth().aspectRatio(358f/168f)) {


            Box(
                modifier = Modifier
                    .weight(168f)
                    .fillMaxHeight(),
            ) {
                AsyncImage(
                    model = artistDetail.profileImageUrl,
                    contentDescription = artistDetail.name,
                    contentScale = ContentScale.Crop,
                    placeholder =
                        painterResource(com.ssafy.motif.core.designsystem.R.drawable.profile_square),
                    error =
                        painterResource(com.ssafy.motif.core.designsystem.R.drawable.profile_square),
                    fallback =
                        painterResource(com.ssafy.motif.core.designsystem.R.drawable.profile_square),
                    modifier = Modifier
                        .matchParentSize()
                )
            }
            Column(
                modifier = Modifier
                    .weight(178f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = artistDetail.name,
                        style = MotifTheme.typography.titleSB20,
                        color = MotifTheme.colors.typo,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    Text(
                        text = artistDetail.groupName,
                        style = MotifTheme.typography.bodySB16,
                        color = MotifTheme.colors.gray400,
                        maxLines = 1,
                    )
                    if(artistDetail.hasMotifPass){
                        Icon(
                            painter = painterResource(MotifIcons.MotifPass),
                            contentDescription = null,
                            modifier = Modifier
                                .size(16.dp),
                        )
                    }

                }

                Row(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "${artistDetail.challengeNum}",
                            style = MotifTheme.typography.bodySB16.copy(lineHeight = 19.sp),
                            color = MotifTheme.colors.typo,
                            maxLines = 1,
                        )
                        Text(
                            text = "Challenges",
                            style = MotifTheme.typography.captionR12.copy(lineHeight = 19.sp),
                            color = MotifTheme.colors.typo,
                            maxLines = 1,
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = formatSubscriberCount(artistDetail.subscribeNum),
                            style = MotifTheme.typography.bodySB16.copy(lineHeight = 19.sp),
                            color = MotifTheme.colors.typo,
                            maxLines = 1,
                        )
                        Text(
                            text = "Followers",
                            style = MotifTheme.typography.captionR12.copy(lineHeight = 19.sp),
                            color = MotifTheme.colors.typo,
                            maxLines = 1,
                        )
                    }

                }


                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(32.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = onAlarmClick,
                        shape = RoundedCornerShape(4.dp),
                        colors = if (isAlarmOn){
                            MotifButtonDefaults.buttonColors(
                                contentColor = MotifTheme.colors.typo,
                                containerColor = MotifTheme.colors.pink
                            )
                        }else {
                            MotifButtonDefaults.buttonColors(
                                contentColor = MotifTheme.colors.typo,
                                containerColor = MotifTheme.colors.gray700
                            )
                        },
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            painter = painterResource(
                                if (isAlarmOn) MotifIcons.Bell
                                else MotifIcons.BellOff
                            ),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Button(
                        onClick = onSubscribeClick,
                        shape = RoundedCornerShape(4.dp),
                        colors = if(isSubscribed){
                            MotifButtonDefaults.buttonColors(
                                containerColor = MotifTheme.colors.gray700,
                                contentColor = MotifTheme.colors.typo,)
                        }else {
                            MotifButtonDefaults.buttonColors(
                                containerColor = MotifTheme.colors.pink,
                                contentColor = MotifTheme.colors.typo,
                            )
                        },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    ) {
                        Text(
                            text = "WITH ${artistDetail.name}",
                            style = MotifTheme.typography.bodySB16,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(4.dp),
            color = MotifTheme.colors.black,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Text(
                text = artistDetail.introducing,
                style = MotifTheme.typography.captionR12.copy(lineHeight = 20.sp),
                color = MotifTheme.colors.typo
            )
        }
    }
}

private fun formatSubscriberCount(count: Long): String = when {
    count < 10_000 -> "%,d명".format(count)
    else -> {
        val man = count / 10_000.0
        if (man % 1.0 == 0.0) "%,.0f만명".format(man)
        else "%,.1f만명".format(man)
    }
}
