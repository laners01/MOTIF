package com.ssafy.motif.feature.ranking.impl.ranking

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject


@HiltViewModel
class RankingViewModel @Inject constructor(): ViewModel(){

    private val _uiState = MutableStateFlow(
        RankingUiState(ranking = RankingMokData.ranking),
    )
    val uiState: StateFlow<RankingUiState> = _uiState.asStateFlow()

    fun playChallenge(chllengeId: Long){

    }
    fun showArtistDetail(chllengeId: Long){

    }
    fun removeQuery(){
        _uiState.update { state -> state.copy(query = "") }
    }
    fun updateQuery(query:String){
        _uiState.update { state -> state.copy(query=query) }
    }
}