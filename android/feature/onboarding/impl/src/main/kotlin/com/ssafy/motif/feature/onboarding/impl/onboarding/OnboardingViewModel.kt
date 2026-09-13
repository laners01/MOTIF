package com.ssafy.motif.feature.onboarding.impl.onboarding

import androidx.lifecycle.ViewModel
import com.ssafy.motif.feature.onboarding.impl.artistselect.ArtistMokData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(): ViewModel() {
    private val _uiState = MutableStateFlow(
        OnboardingUiState(artists = ArtistMokData.artists),
    )
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    fun toggleArtist(artistId: Long) {
        _uiState.update { state ->
            val selected = state.selectedArtists
            state.copy(
                selectedArtists = if (artistId in selected) {
                    selected - artistId
                } else {
                    selected + artistId
                },
            )
        }
    }

    fun removeQuery(){
        _uiState.update { state -> state.copy(query = "") }
    }

    fun updateQuery(query:String){
        _uiState.update { state -> state.copy(query=query) }
    }

    fun updateNickname(nickname: String){
        if(nickname.length> OnboardingUiState.MAX_NICKNAME_LENGTH) return
        if(!NICKNAME_PATTERN.matches(nickname))return
        _uiState.update { state -> state.copy(nickname = nickname) }
    }

    fun removeNickname(){
        _uiState.update { state -> state.copy(nickname = "") }
    }

    fun goToNicknameInput(){
        _uiState.update { state -> state.copy(step = OnboardingStep.NicknameInput) }
    }

    fun goToArtistSelect(){
        _uiState.update { state -> state.copy(step = OnboardingStep.ArtistSelect) }
    }

    companion object{
        private val NICKNAME_PATTERN =
            Regex("^[가-힣ㄱ-ㅎㅏ-ㅣa-zA-Z._0-9]*\$")
    }
}
