package com.ssafy.motif.feature.mypage.impl.mypage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MyPageViewModel @Inject constructor(): ViewModel() {

    private val _uiState =
        MutableStateFlow<MyPageUiState>(MyPageUiState())
    internal val uiState: StateFlow<MyPageUiState> =
        _uiState.asStateFlow()


    fun notificationClick(){

    }
    fun settingClick(){

    }
    private fun loadProfile(){
        viewModelScope.launch {
            _uiState.update{
                it.copy(profileState = ProfileUiState.Loading)
            }
            _uiState.update {
                it.copy(
                    profileState = ProfileUiState.Success(
                        profile = MyPageMockData.profile,
                    ),
                )
            }
        }
    }

    fun selectTab(tab: MyPageTab){
        _uiState.update{state -> state.copy(selectedTab = tab)}
    }
    init{
        loadProfile()
        loadChallenges()
    }
    private fun loadChallenges(){
        viewModelScope.launch {
            _uiState.update { it.copy(challengeState = MyChallengeUiState.Loading) }

            val firstPage = MyPageMockData.challenges.take(CHALLENGE_PAGE_SIZE)
            _uiState.update {
                it.copy(
                    challengeState = MyChallengeUiState.Success(
                        challenges = firstPage,
                        hasNext = firstPage.size < MyPageMockData.challenges.size,
                    ),
                )
            }
        }
    }

    fun onLoadMoreChallenges(){
        val current = _uiState.value.challengeState as? MyChallengeUiState.Success ?: return
        if (!current.hasNext) return
        if (current.appendState !is MyChallengeAppendUiState.Idle) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    challengeState = current.copy(
                        appendState = MyChallengeAppendUiState.Loading,
                    ),
                )
            }

            val loaded = current.challenges.size
            val next = MyPageMockData.challenges
                .drop(loaded)
                .take(CHALLENGE_PAGE_SIZE)

            _uiState.update {
                it.copy(
                    challengeState = MyChallengeUiState.Success(
                        challenges = current.challenges + next,
                        hasNext = loaded + next.size < MyPageMockData.challenges.size,
                    ),
                )
            }
        }
    }

    companion object {
        private const val CHALLENGE_PAGE_SIZE = 9
    }
}