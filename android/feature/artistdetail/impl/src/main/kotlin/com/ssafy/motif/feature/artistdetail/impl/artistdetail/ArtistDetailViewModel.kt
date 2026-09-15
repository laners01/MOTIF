package com.ssafy.motif.feature.artistdetail.impl.artistdetail

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class ArtistDetailViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(ArtistDetailUiState(artistDetail = ArtistDetailMokData.artist))
    val uiState: StateFlow<ArtistDetailUiState> = _uiState.asStateFlow()

    fun toggleSubscribe() {
        _uiState.update { state ->
            val detail = state.artistDetail ?: return@update state
            state.copy(
                artistDetail = detail.copy(
                    isSubscribed = !detail.isSubscribed,
                    subscribeNum = detail.subscribeNum + if (detail.isSubscribed) -1 else 1,
                ),
            )
        }
    }

    fun toggleAlarm(){
        _uiState.update { state ->
            val detail = state.artistDetail ?: return@update state
            state.copy(
                artistDetail = detail.copy(
                    isAlarmOn = !detail.isAlarmOn,
                ),
            )
        }
    }

    fun goBackScreen(){

    }



}
