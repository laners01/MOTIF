package com.ssafy.motif.feature.home.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.motif.core.common.result.Result
import com.ssafy.motif.core.common.result.asResult
import com.ssafy.motif.core.data.repository.ArtistRepository
import com.ssafy.motif.core.data.repository.ChallengeRepository
import com.ssafy.motif.core.model.artist.ArtistFilter
import com.ssafy.motif.core.ui.comment.CommentUiState
import com.ssafy.motif.feature.home.impl.model.FanRadarUiState
import com.ssafy.motif.feature.home.impl.model.MyArtistFeedUiState
import com.ssafy.motif.feature.home.impl.model.OnstageUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@HiltViewModel
internal class HomeViewModel @Inject constructor(
    private val challengeRepository: ChallengeRepository,
    private val artistRepository: ArtistRepository,
) : ViewModel() {

    private val _onstageUiState =
        MutableStateFlow<OnstageUiState>(
            OnstageUiState.Loading,
        )
    val onstageUiState: StateFlow<OnstageUiState> =
        _onstageUiState.asStateFlow()

    private val _fanRadarUiState =
        MutableStateFlow<FanRadarUiState>(
            FanRadarUiState.Loading,
        )
    val fanRadarUiState: StateFlow<FanRadarUiState> =
        _fanRadarUiState.asStateFlow()

    /*
     * TODO:
     * 구독 아티스트 통합 피드 Repository 구현 후 연결한다.
     *
     * 현재는 Network / Repository가 없으므로
     * Loading 상태까지만 ViewModel이 소유한다.
     */
    private val _myArtistFeedUiState =
        MutableStateFlow<MyArtistFeedUiState>(
            MyArtistFeedUiState.Loading,
        )
    val myArtistFeedUiState: StateFlow<MyArtistFeedUiState> =
        _myArtistFeedUiState.asStateFlow()

    /*
     * TODO:
     * CommentRepository 구현 후 연결한다.
     */
    private val _commentUiState =
        MutableStateFlow(
            CommentUiState(),
        )
    val commentUiState: StateFlow<CommentUiState> =
        _commentUiState.asStateFlow()

    private var onstageJob: Job? = null
    private var fanRadarJob: Job? = null

    init {
        loadOnstage()
        loadFanRadar()
    }

    fun retryOnstage() {
        loadOnstage()
    }

    fun retryFanRadar() {
        loadFanRadar()
    }

    private fun loadOnstage() {
        onstageJob?.cancel()

        onstageJob =
            challengeRepository
                .getHotChallengeParticipations(
                    cursor = null,
                    size = ONSTAGE_SIZE,
                )
                .asResult()
                .onEach { result ->
                    _onstageUiState.value =
                        when (result) {
                            Result.Loading -> {
                                OnstageUiState.Loading
                            }

                            is Result.Success -> {
                                OnstageUiState.Success(
                                    items = result.data.items,
                                )
                            }

                            is Result.Error -> {
                                OnstageUiState.Error(
                                    exception = result.exception,
                                )
                            }
                        }
                }
                .launchIn(viewModelScope)
    }

    private fun loadFanRadar() {
        fanRadarJob?.cancel()

        fanRadarJob =
            artistRepository
                .getArtistRanking(
                    filter = ArtistFilter.ALL,
                    page = FIRST_PAGE,
                    size = FAN_RADAR_SIZE,
                )
                .asResult()
                .onEach { result ->
                    _fanRadarUiState.value =
                        when (result) {
                            Result.Loading -> {
                                FanRadarUiState.Loading
                            }

                            is Result.Success -> {
                                FanRadarUiState.Success(
                                    artists = result.data.items,
                                )
                            }

                            is Result.Error -> {
                                FanRadarUiState.Error(
                                    exception = result.exception,
                                )
                            }
                        }
                }
                .launchIn(viewModelScope)
    }

    private companion object {
        const val FIRST_PAGE = 0
        const val ONSTAGE_SIZE = 10
        const val FAN_RADAR_SIZE = 4
    }
}