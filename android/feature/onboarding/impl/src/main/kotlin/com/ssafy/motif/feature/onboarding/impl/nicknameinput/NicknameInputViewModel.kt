package com.ssafy.motif.feature.onboarding.impl.nicknameinput

import androidx.lifecycle.ViewModel
import com.ssafy.motif.feature.onboarding.impl.artistselect.ArtistMokData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class NicknameInputViewModel @Inject constructor(): ViewModel() {
    private val _uiState = MutableStateFlow(
        NicknameInputUiState())
    val uiState: StateFlow<NicknameInputUiState> = _uiState.asStateFlow()

    fun updateNickname(nickname: String){
        if(nickname.length> NicknameInputUiState.MAX_NICKNAME_LENGTH) return
        if(!NICKNAME_PATTERN.matches(nickname))return
        _uiState.update { state -> state.copy(nickname = nickname) }
    }

    companion object{
        private val NICKNAME_PATTERN =
            Regex("^[가-힣ㄱ-ㅎㅏ-ㅣa-zA-Z._0-9]*\$")
    }

    fun removeNickname(){
        _uiState.update { state -> state.copy(nickname = "") }
    }

    fun setLikedArtists(artistIds: List<Long>){
        val artists = ArtistMokData.artists.filter{ it.id in artistIds }
        _uiState.update { state -> state.copy(likedAritsts = artists) }
    }
}