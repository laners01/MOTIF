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
        loadRepresentativeMotif()
        loadLiked()
    }

    private fun loadRepresentativeMotif() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(representativeMotif = MyPageMockData.representativeMotif)
            }
        }
    }

    private fun loadArtists() {
        viewModelScope.launch {
            _uiState.update { it.copy(artistState = MyArtistUiState.Loading) }

            _uiState.update {
                it.copy(
                    artistState = MyArtistUiState.Success(
                        artists = MyPageMockData.artistSubscriptions,
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

    private fun loadLiked() {
        viewModelScope.launch {
            _uiState.update { it.copy(likedState = MyLikedUiState.Loading) }

            val firstPage = MyPageMockData.likedChallenges.take(LIKED_PAGE_SIZE)
            _uiState.update {
                it.copy(
                    likedState = MyLikedUiState.Success(
                        challenges = firstPage,
                        hasNext = firstPage.size < MyPageMockData.likedChallenges.size,
                    ),
                )
            }
        }
    }
    fun onLikedFocusChange(index: Int) {
        _uiState.update { it.copy(likedFocusedIndex = index) }

        val current = _uiState.value.likedState as? MyLikedUiState.Success ?: return

        // 끝에서 2장 남으면 미리 불러온다
        if (index >= current.challenges.size - LIKED_LOAD_MORE_THRESHOLD) {
            onLoadMoreLiked()
        }
    }

    fun onLoadMoreLiked() {
        val current = _uiState.value.likedState as? MyLikedUiState.Success ?: return
        if (!current.hasNext) return
        if (current.appendState !is MyLikedAppendUiState.Idle) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    likedState = current.copy(
                        appendState = MyLikedAppendUiState.Loading,
                    ),
                )
            }

            val loaded = current.challenges.size
            val next = MyPageMockData.likedChallenges
                .drop(loaded)
                .take(LIKED_PAGE_SIZE)

            _uiState.update {
                it.copy(
                    likedState = MyLikedUiState.Success(
                        challenges = current.challenges + next,
                        hasNext = loaded + next.size < MyPageMockData.likedChallenges.size,
                    ),
                )
            }
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

    fun onProfileImageSelected(uri: String?){
        if(uri == null)return   //취소시 기존 이미지 유지

        val current = (_uiState.value.profileState as? ProfileUiState.Success)
            ?.profile?: return

        _uiState.update{
            it.copy(
                profileState = ProfileUiState.Success(
                    profile = current.copy(profileImageUrl = uri),
                )
            )
        }
    }
    companion object {
        private const val CHALLENGE_PAGE_SIZE = 9
        private const val MAX_NICKNAME_LENGTH = 20
        private const val LIKED_PAGE_SIZE = 6
        private const val LIKED_LOAD_MORE_THRESHOLD = 2
        private val NICKNAME_PATTERN = Regex("^[가-힣ㄱ-ㅎㅏ-ㅣa-zA-Z._0-9]*\$")
    }
}