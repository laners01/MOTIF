package com.ssafy.motif.feature.mypage.impl.mypage

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.motif.core.designsystem.component.LoadingWheel
import com.ssafy.motif.core.designsystem.component.Surface
import com.ssafy.motif.feature.mypage.impl.mypage.component.MyProfileSection

@Composable
fun MyPageScreen(
    modifier: Modifier = Modifier,
    viewModel: MyPageViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    MyPageScreen(
        uiState = uiState,
        onNotificationClick = viewModel::notificationClick,
        onSettingsClick = viewModel::settingClick,
        modifier = modifier,
    )
}
@Composable
internal fun MyPageScreen(
    uiState: MyPageUiState,
    onNotificationClick:() -> Unit,
    onSettingsClick:() -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize()) {
        when (val profileState = uiState.profileState) {
            ProfileUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    LoadingWheel()
                }
            }

            is ProfileUiState.Success -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    MyProfileSection(profile = profileState.profile,
                        onNotificationClick = onNotificationClick,
                        onSettingsClick = onSettingsClick,)
                }
            }

            is ProfileUiState.Error -> {
                // TODO 프로필 조회 실패 UI
            }
        }
    }
}
