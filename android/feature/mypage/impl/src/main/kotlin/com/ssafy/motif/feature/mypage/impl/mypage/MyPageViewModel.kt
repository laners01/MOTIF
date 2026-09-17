package com.ssafy.motif.feature.mypage.impl.mypage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

class MyPageViewModel @Inject constructor(): ViewModel() {

    private val _uiState =
        MutableStateFlow<MyPageUiState>(MyPageUiState.Loading)
    internal val uiState: StateFlow<MyPageUiState> =
        _uiState.asStateFlow()

    init {
        loadProfile()
    }

    private fun loadProfile(){
        viewModelScope.launch {
            _uiState.value = MyPageUiState.Loading

            _uiState.value = MyPageUiState.Success(
                profile = MyPageMockData.profile,
            )
        }
    }
}