package com.ssafy.motif.feature.onboarding.impl.nicknameinput

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.motif.core.designsystem.component.Button
import com.ssafy.motif.core.designsystem.component.Icon
import com.ssafy.motif.core.designsystem.component.IconButton
import com.ssafy.motif.core.designsystem.component.ModifIconButtonDefaults
import com.ssafy.motif.core.designsystem.component.MotifButtonDefaults
import com.ssafy.motif.core.designsystem.component.Surface
import com.ssafy.motif.core.designsystem.component.Text
import com.ssafy.motif.core.designsystem.icon.MotifIcons
import com.ssafy.motif.core.designsystem.theme.MotifTheme
import com.ssafy.motif.feature.onboarding.impl.artistselect.ArtistCard

@Composable
fun NicknameInputRoute(
    artistIds: List<Long>,
    onCompleteClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NicknameInputViewModel = hiltViewModel(),
){
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(artistIds) {
        viewModel.setLikedArtists(artistIds)
    }

    NicknameInputScreen(
        uiState = uiState,
        onNicknameChange = viewModel::updateNickname,
        onRemoveClick = viewModel::removeNickname,
        onCompleteClick = onCompleteClick,
        modifier = modifier,
    )
}

@Composable
internal fun NicknameInputScreen(
    uiState: NicknameInputUiState,
    onNicknameChange:(String) -> Unit,
    onRemoveClick:() -> Unit,
    onCompleteClick:() -> Unit,     //완료 버튼 눌렀을 때
    modifier: Modifier = Modifier,
){
    Column(modifier = modifier.fillMaxSize()) {


        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 20.dp, top = 20.dp, end = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),    //사이 좌우간격
        ) {
            items(uiState.likedAritsts,key = {it.id}){
                artist -> ArtistCard(
                    artist = artist,
                    selected = false,
                    onClick = {},
                    modifier = Modifier.width(100.dp)
                )
            }
        }

        Text(
            text = "이름",
            style = MotifTheme.typography.titleSB18,
            color = MotifTheme.colors.white,
            modifier = modifier
                .padding(start = 20.dp, top = 20.dp)
        )
        Text(
            text = "이름은 언제든지 재설정이 가능합니다.",
            style = MotifTheme.typography.bodySB14,
            color = MotifTheme.colors.gray400,
            modifier = modifier
                .padding(start = 20.dp, top = 5.dp)
        )

        NicknameInputBar(
            nickname = uiState.nickname,
            onNicknameChange = onNicknameChange,
            onRemoveClick = onRemoveClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, top = 20.dp, end = 20.dp),
        )
        Text(
            text = "${uiState.nickname.length}/${NicknameInputUiState.MAX_NICKNAME_LENGTH}",
            style = MotifTheme.typography.bodySB14,
            color = MotifTheme.colors.gray400,
            modifier = modifier
                .padding(end = 20.dp, top = 10.dp)
                .align(Alignment.End)
        )
        Button(
            onClick = onCompleteClick,
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
        ){
            Text(
                text = "완료",
                style = MotifTheme.typography.titleSB18,
            )


        }
    }
}

@Composable
private fun NicknameInputBar(
    nickname: String,
    onNicknameChange:(String) ->Unit,
    onRemoveClick:() -> Unit,
    modifier: Modifier = Modifier,
){
    Surface(
        modifier = modifier,
        color = MotifTheme.colors.gray700,
        shape = RoundedCornerShape(10.dp),
        contentPadding = PaddingValues(horizontal = 7.dp, vertical = 12.dp),
    ){
        Row(verticalAlignment = Alignment.CenterVertically){
            BasicTextField(
                value = nickname,
                onValueChange = onNicknameChange,
                singleLine = true,
                textStyle = MotifTheme.typography.bodyR14.copy(
                    color = MotifTheme.colors.white,
                ),
                cursorBrush = SolidColor(MotifTheme.colors.white),
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart,
                        modifier = Modifier
                            .padding(top = 5.dp, bottom = 5.dp)) {
                    if (nickname.isEmpty()) {
                        Text(
                            text = "닉네임은 20자까지 입력이 가능합니다",
                            style = MotifTheme.typography.bodyR14,
                            color = MotifTheme.colors.white,

                            )
                    }
                    innerTextField()
                }
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