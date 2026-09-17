package com.ssafy.motif.feature.mypage.impl.mypage

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.motif.core.designsystem.component.Surface

@Composable
fun MyPageScreen(
    modifier: Modifier = Modifier,
    viewModel: MyPageViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    MyPageScreen(
        uiState = uiState,
        modifier = modifier,
    )
}
@Composable
internal fun MyPageScreen(
    uiState: MyPageUiState,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize()) {
        when (uiState) {
            MyPageUiState.Loading -> { /* 4번에서 */ }
            is MyPageUiState.Success -> { /* 4번에서 프로필 영역 */ }
            is MyPageUiState.Error -> { /* 4번에서 */ }
        }
    }
}
