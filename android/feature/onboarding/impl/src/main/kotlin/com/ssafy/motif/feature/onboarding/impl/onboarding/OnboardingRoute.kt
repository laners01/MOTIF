package com.ssafy.motif.feature.onboarding.impl.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.motif.feature.onboarding.impl.artistselect.ArtistSelectScreen
import com.ssafy.motif.feature.onboarding.impl.nicknameinput.NicknameInputScreen

@Composable
fun OnboardingRoute(
    onCompleteClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // 닉네임 입력 단계에서 뒤로 가면 온보딩을 벗어나지 않고 아티스트 선택 단계로 돌아간다.
    BackHandler(enabled = uiState.step == OnboardingStep.NicknameInput) {
        viewModel.goToArtistSelect()
    }

    when (uiState.step) {
        OnboardingStep.ArtistSelect -> ArtistSelectScreen(
            uiState = uiState,
            onArtistClick = viewModel::toggleArtist,
            onQueryChange = viewModel::updateQuery,
            onRemoveClick = viewModel::removeQuery,
            onNextClick = viewModel::goToNicknameInput,
            modifier = modifier,
        )

        OnboardingStep.NicknameInput -> NicknameInputScreen(
            uiState = uiState,
            onNicknameChange = viewModel::updateNickname,
            onRemoveClick = viewModel::removeNickname,
            onCompleteClick = onCompleteClick,
            modifier = modifier,
        )
    }
}
