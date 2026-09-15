package com.ssafy.motif.feature.artistdetail.impl.artistdetail

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.motif.core.designsystem.component.Surface
import com.ssafy.motif.feature.artistdetail.impl.artistdetail.component.ArtistDetailCard

@Composable
fun ArtistDetailScreen(
    artistId: Long,
    modifier: Modifier = Modifier,
    viewModel: ArtistDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ArtistDetailScreen(
        uiState = uiState,
        onSubscribeClick = viewModel::toggleSubscribe,
        onAlarmClick = viewModel::toggleAlarm,
        onBackClick = viewModel::goBackScreen,
        modifier = modifier,
    )
}

@Composable
internal fun ArtistDetailScreen(
    uiState: ArtistDetailUiState,
    onSubscribeClick: () -> Unit,    //구독버튼 클릭시
    onAlarmClick:() -> Unit,        //알람버튼 클릭시
    onBackClick:() -> Unit,         //뒤로가기 클릭시
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize()) {
        uiState.artistDetail?.let{ artistDetail ->
            ArtistDetailCard(
                artistDetail = artistDetail,
                isAlarmOn = artistDetail.isAlarmOn,
                isSubscribed = artistDetail.isSubscribed,
                onSubscribeClick = onSubscribeClick,
                onAlarmClick = onAlarmClick,
                onBackClick = onBackClick,
            )
        }
    }
}
