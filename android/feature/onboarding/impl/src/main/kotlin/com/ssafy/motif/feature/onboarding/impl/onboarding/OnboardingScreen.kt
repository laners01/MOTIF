package com.ssafy.motif.feature.onboarding.impl.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun OnboardingScreen(
    onCompleteClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // 닉네임 입력 단계에서 뒤로 가면 온보딩을 벗어나지 않고 아티스트 선택 단계로 돌아간다.
    BackHandler(enabled = uiState.step == OnboardingStep.NicknameInput) {
        viewModel.goToArtistSelect()
    }

    OnboardingScreen(
        uiState = uiState,
        onArtistClick = viewModel::toggleArtist,
        onQueryChange = viewModel::updateQuery,
        onQueryRemoveClick = viewModel::removeQuery,
        onNextClick = viewModel::goToNicknameInput,
        onNicknameChange = viewModel::updateNickname,
        onNicknameRemoveClick = viewModel::removeNickname,
        onCompleteClick = onCompleteClick,
        modifier = modifier,
    )
}

@Composable
internal fun OnboardingScreen(
    uiState: OnboardingUiState,
    onArtistClick: (Long) -> Unit,          //아티스트 클릭시
    onQueryChange: (String) -> Unit,        //검색어 변경시
    onQueryRemoveClick: () -> Unit,         //검색어 삭제
    onNextClick: () -> Unit,                //n명 선택 버튼 클릭 시
    onNicknameChange: (String) -> Unit,     //닉네임 변경시
    onNicknameRemoveClick: () -> Unit,      //닉네임 삭제
    onCompleteClick: () -> Unit,            //완료 버튼 눌렀을 때
    modifier: Modifier = Modifier,
) {
    when (uiState.step) {
        OnboardingStep.ArtistSelect -> ArtistSelectScreen(
            uiState = uiState,
            onArtistClick = onArtistClick,
            onQueryChange = onQueryChange,
            onRemoveClick = onQueryRemoveClick,
            onNextClick = onNextClick,
            modifier = modifier,
        )

        OnboardingStep.NicknameInput -> NicknameInputScreen(
            uiState = uiState,
            onNicknameChange = onNicknameChange,
            onRemoveClick = onNicknameRemoveClick,
            onCompleteClick = onCompleteClick,
            modifier = modifier,
        )
    }
}
