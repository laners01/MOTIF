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


    fun onCreateClick(){

    }
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
        loadArtists()
    }

    private fun loadArtists() {
        viewModelScope.launch {
            _uiState.update { it.copy(artistState = MyArtistUiState.Loading) }

            _uiState.update {
                it.copy(
                    artistState = MyArtistUiState.Success(
                        artists = MyPageMockData.subscribedArtists,
                    ),
                )
            }
        }
    }
    fun onArtistCardClick(artistId: Long) {
        _uiState.update { state ->
            state.copy(
                flippedArtistId = if (state.flippedArtistId == artistId) null else artistId,
            )
        }
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

    fun onNicknameEditStart(){// 현재 닉네임으로 편집 상태 진입
        val current = (_uiState.value.profileState as? ProfileUiState.Success)
            ?.profile ?: return

        _uiState.update {
            it.copy(
                nicknameEdit = NicknameEditUiState(
                    value = current.nickname,
                    isValid = true,
                ),
            )
        }
    }
    fun onNicknameChange(value:String){// 검증 통과분만 반영
        if (value.length > MAX_NICKNAME_LENGTH) return
        if (!NICKNAME_PATTERN.matches(value)) return

        _uiState.update { state ->
            state.copy(
                nicknameEdit = state.nicknameEdit?.copy(
                    value = value,
                    isValid = value.isNotBlank(),
                ),
            )
        }
    }
    fun onNicknameEditDone(){// 저장 후 조회 상태 복귀
        val edit = _uiState.value.nicknameEdit ?: return
        val current = (_uiState.value.profileState as? ProfileUiState.Success)
            ?.profile ?: return

        // 빈 값이면 완료 불가
        if (edit.value.isBlank()) return

        // 변경 없음 → 저장 요청 없이 복귀
        if (edit.value == current.nickname) {
            _uiState.update { it.copy(nicknameEdit = null) }
            return
        }

        _uiState.update {
            it.copy(
                profileState = ProfileUiState.Success(
                    profile = current.copy(nickname = edit.value),
                ),
                nicknameEdit = null,
            )
        }
    }
    fun onNicknameEditCancel(){ // 폐기
        _uiState.update { it.copy(nicknameEdit = null) }
    }
    companion object {
        private const val CHALLENGE_PAGE_SIZE = 9
        private const val MAX_NICKNAME_LENGTH = 20
        private val NICKNAME_PATTERN = Regex("^[가-힣ㄱ-ㅎㅏ-ㅣa-zA-Z._0-9]*\$")
    }
}