package com.ssafy.motif.feature.motif.impl

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun MotifScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MotifViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    MotifScreen(
        uiState = uiState,
        onMenuClick = viewModel::selectMenu,
        onBackClick = onBackClick,
        modifier = modifier,
    )
}

@Composable
internal fun MotifScreen(
    uiState: MotifUiState,
    onMenuClick: (MotifSubMenu) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize())
}
