package com.ssafy.motif.feature.onboarding.impl.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
internal fun OnboardingScreen(
    onCompleteClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val uiState by
    viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.isCompleted) {
        if (uiState.isCompleted) {
            viewModel.consumeCompleteEvent()
            onCompleteClick()
        }
    }

    BackHandler(
        enabled = uiState.step == OnboardingStep.NicknameInput,
    ) {
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
        onCompleteClick = viewModel::completeOnboarding,
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(
                WindowInsets.statusBars,
            ),
    )
}

@Composable
private fun OnboardingScreen(
    uiState: OnboardingUiState,
    onArtistClick: (Long) -> Unit,
    onQueryChange: (String) -> Unit,
    onQueryRemoveClick: () -> Unit,
    onNextClick: () -> Unit,
    onNicknameChange: (String) -> Unit,
    onNicknameRemoveClick: () -> Unit,
    onCompleteClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (uiState.step) {
        OnboardingStep.ArtistSelect -> {
            ArtistSelectScreen(
                uiState = uiState,
                onArtistClick = onArtistClick,
                onQueryChange = onQueryChange,
                onRemoveClick = onQueryRemoveClick,
                onNextClick = onNextClick,
                modifier = modifier,
            )
        }

        OnboardingStep.NicknameInput -> {
            NicknameInputScreen(
                uiState = uiState,
                onNicknameChange = onNicknameChange,
                onRemoveClick = onNicknameRemoveClick,
                onCompleteClick = onCompleteClick,
                modifier = modifier,
            )
        }
    }
}