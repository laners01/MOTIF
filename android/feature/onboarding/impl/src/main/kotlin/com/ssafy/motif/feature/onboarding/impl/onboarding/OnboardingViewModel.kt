package com.ssafy.motif.feature.onboarding.impl.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.motif.core.common.result.Result
import com.ssafy.motif.core.common.result.asResult
import com.ssafy.motif.core.data.repository.ArtistRepository
import com.ssafy.motif.core.data.repository.AuthRepository
import com.ssafy.motif.core.model.artist.ArtistFilter
import com.ssafy.motif.core.model.onboarding.OnboardingCompletionResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
internal class OnboardingViewModel @Inject constructor(
    private val artistRepository: ArtistRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState =
        MutableStateFlow(
            OnboardingUiState(),
        )

    val uiState: StateFlow<OnboardingUiState> =
        _uiState.asStateFlow()

    private var artistJob: Job? = null
    private var searchJob: Job? = null
    private var onboardingJob: Job? = null

    init {
        loadArtists()
    }

    fun toggleArtist(
        artistId: Long,
    ) {
        _uiState.update { state ->
            val selectedArtists =
                if (
                    artistId in
                    state.selectedArtists
                ) {
                    state.selectedArtists -
                            artistId
                } else {
                    state.selectedArtists +
                            artistId
                }

            val selectedArtistItems =
                (
                        state.artists +
                                state.searchResults
                        )
                    .distinctBy { artist ->
                        artist.id
                    }
                    .filter { artist ->
                        artist.id in
                                selectedArtists
                    }

            state.copy(
                selectedArtists =
                    selectedArtists,
                selectedArtistItems =
                    selectedArtistItems,
            )
        }
    }

    fun updateQuery(
        query: String,
    ) {
        _uiState.update { state ->
            state.copy(
                query =
                    query,
            )
        }

        searchJob?.cancel()

        if (query.isBlank()) {
            _uiState.update { state ->
                state.copy(
                    searchResults =
                        emptyList(),
                    isSearching =
                        false,
                )
            }

            return
        }

        searchJob =
            viewModelScope.launch {
                delay(
                    SEARCH_DEBOUNCE_MILLIS,
                )

                searchArtists(
                    query =
                        query,
                )
            }
    }

    fun removeQuery() {
        searchJob?.cancel()

        _uiState.update { state ->
            state.copy(
                query =
                    "",
                searchResults =
                    emptyList(),
                isSearching =
                    false,
            )
        }
    }

    fun updateNickname(
        nickname: String,
    ) {
        if (
            nickname.length >
            OnboardingUiState
                .MAX_NICKNAME_LENGTH
        ) {
            return
        }

        _uiState.update { state ->
            state.copy(
                nickname =
                    nickname,
                error =
                    null,
            )
        }
    }

    fun removeNickname() {
        _uiState.update { state ->
            state.copy(
                nickname =
                    "",
            )
        }
    }

    fun goToNicknameInput() {
        if (
            _uiState
                .value
                .selectedArtists
                .size <
            OnboardingUiState
                .MIN_SELECTION_COUNT
        ) {
            return
        }

        _uiState.update { state ->
            state.copy(
                step =
                    OnboardingStep
                        .NicknameInput,
            )
        }
    }

    fun goToArtistSelect() {
        _uiState.update { state ->
            state.copy(
                step =
                    OnboardingStep
                        .ArtistSelect,
            )
        }
    }

    /*
     * 실제 온보딩 완료.
     *
     * 서버에 구독 아티스트와 닉네임을 저장한다.
     */
    fun completeOnboarding() {
        val state =
            _uiState.value

        if (
            state.isSubmitting ||
            !state.canProceed
        ) {
            return
        }

        onboardingJob?.cancel()

        onboardingJob =
            authRepository
                .submitOnboarding(
                    artistIds =
                        state.selectedArtists
                            .toList(),
                    nickname =
                        state.nickname,
                )
                .asResult()
                .onEach { result ->
                    when (result) {
                        Result.Loading -> {
                            _uiState.update { currentState ->
                                currentState.copy(
                                    isSubmitting =
                                        true,
                                    error =
                                        null,
                                )
                            }
                        }

                        is Result.Success -> {
                            handleOnboardingSuccess(
                                result =
                                    result.data,
                            )
                        }

                        is Result.Error -> {
                            _uiState.update { currentState ->
                                currentState.copy(
                                    isSubmitting =
                                        false,
                                    error =
                                        OnboardingError
                                            .SubmitFailed,
                                )
                            }
                        }
                    }
                }
                .launchIn(
                    viewModelScope,
                )
    }

    /*
     * Demo Mode 전용.
     *
     * 서버 API / AuthRepository / DataStore를 전혀 건드리지 않는다.
     * 입력된 아티스트와 닉네임은 현재 OnboardingUiState에만 존재한다.
     */
    fun completeDemoOnboarding() {
        val state =
            _uiState.value

        if (
            state.isSubmitting ||
            !state.canProceed
        ) {
            return
        }

        _uiState.update { currentState ->
            currentState.copy(
                isCompleted =
                    true,
                error =
                    null,
            )
        }
    }

    fun consumeCompleteEvent() {
        _uiState.update { state ->
            state.copy(
                isCompleted =
                    false,
            )
        }
    }

    private fun loadArtists() {
        artistJob?.cancel()

        artistJob =
            artistRepository
                .getArtistRanking(
                    filter =
                        ArtistFilter.ALL,
                    page =
                        FIRST_PAGE,
                    size =
                        ARTIST_SIZE,
                )
                .asResult()
                .onEach { result ->
                    when (result) {
                        Result.Loading -> {
                            _uiState.update { state ->
                                state.copy(
                                    isLoading =
                                        true,
                                    error =
                                        null,
                                )
                            }
                        }

                        is Result.Success -> {
                            _uiState.update { state ->
                                state.copy(
                                    artists =
                                        result
                                            .data
                                            .items
                                            .map { rankedArtist ->
                                                rankedArtist.artist
                                            },
                                    isLoading =
                                        false,
                                    error =
                                        null,
                                )
                            }
                        }

                        is Result.Error -> {
                            _uiState.update { state ->
                                state.copy(
                                    isLoading =
                                        false,
                                    error =
                                        OnboardingError
                                            .ArtistLoadFailed,
                                )
                            }
                        }
                    }
                }
                .launchIn(
                    viewModelScope,
                )
    }

    private fun searchArtists(
        query: String,
    ) {
        searchJob =
            artistRepository
                .searchArtists(
                    query =
                        query.trim(),
                    size =
                        SEARCH_SIZE,
                )
                .asResult()
                .onEach { result ->
                    /*
                     * 요청 중 검색어가 바뀐 경우
                     * 이전 검색 결과는 반영하지 않는다.
                     */
                    if (
                        _uiState.value.query !=
                        query
                    ) {
                        return@onEach
                    }

                    when (result) {
                        Result.Loading -> {
                            _uiState.update { state ->
                                state.copy(
                                    isSearching =
                                        true,
                                    error =
                                        null,
                                )
                            }
                        }

                        is Result.Success -> {
                            _uiState.update { state ->
                                state.copy(
                                    searchResults =
                                        result
                                            .data
                                            .map {
                                                    artistWithSubscription,
                                                ->
                                                artistWithSubscription.artist
                                            },
                                    isSearching =
                                        false,
                                    error =
                                        null,
                                )
                            }
                        }

                        is Result.Error -> {
                            _uiState.update { state ->
                                state.copy(
                                    searchResults =
                                        emptyList(),
                                    isSearching =
                                        false,
                                    error =
                                        OnboardingError
                                            .SearchFailed,
                                )
                            }
                        }
                    }
                }
                .launchIn(
                    viewModelScope,
                )
    }

    private fun handleOnboardingSuccess(
        result:
        OnboardingCompletionResult,
    ) {
        when (result) {
            is OnboardingCompletionResult.Completed,
            OnboardingCompletionResult.AlreadyCompleted,
                -> {
                _uiState.update { state ->
                    state.copy(
                        isSubmitting =
                            false,
                        isCompleted =
                            true,
                        error =
                            null,
                    )
                }
            }

            OnboardingCompletionResult.ArtistNotFound -> {
                _uiState.update { state ->
                    state.copy(
                        step =
                            OnboardingStep
                                .ArtistSelect,
                        selectedArtists =
                            emptySet(),
                        selectedArtistItems =
                            emptyList(),
                        query =
                            "",
                        searchResults =
                            emptyList(),
                        isSubmitting =
                            false,
                        error =
                            OnboardingError
                                .ArtistNotFound,
                    )
                }

                loadArtists()
            }

            OnboardingCompletionResult.InvalidParameter -> {
                _uiState.update { state ->
                    state.copy(
                        isSubmitting =
                            false,
                        error =
                            OnboardingError
                                .InvalidParameter,
                    )
                }
            }
        }
    }

    private companion object {
        const val FIRST_PAGE =
            0

        const val ARTIST_SIZE =
            20

        const val SEARCH_SIZE =
            20

        const val SEARCH_DEBOUNCE_MILLIS =
            300L
    }
}