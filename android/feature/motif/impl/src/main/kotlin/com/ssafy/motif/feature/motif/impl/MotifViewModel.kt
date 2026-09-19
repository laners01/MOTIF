package com.ssafy.motif.feature.motif.impl

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class MotifViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(MotifUiState())
    internal val uiState: StateFlow<MotifUiState> = _uiState.asStateFlow()

    fun selectMenu(menu: MotifSubMenu) {
        _uiState.update { state -> state.copy(selectedMenu = menu) }
    }
}
