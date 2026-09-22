package com.ssafy.motif.feature.capture.impl.challenge

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.motif.core.model.capture.CapturePosition
import com.ssafy.motif.feature.capture.impl.challenge.model.CaptureStage
import com.ssafy.motif.feature.capture.impl.challenge.model.CaptureUiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class CaptureViewModel : ViewModel() {

    private val _uiState =
        MutableStateFlow(CaptureUiState())

    val uiState =
        _uiState.asStateFlow()

    private var countdownJob: Job? = null

    fun setCaptureData(
        originalArtistId: Long,
        positions: List<CapturePosition>,
        durationMs: Long,
    ) {
        val defaultPosition =
            positions.firstOrNull { position ->
                position.artist.id != originalArtistId
            }

        _uiState.value =
            CaptureUiState(
                originalArtistId = originalArtistId,
                positions = positions,
                selectedPositionArtistId = defaultPosition?.artist?.id,
                durationMs = durationMs,
            )
    }

    fun onPositionClick(
        artistId: Long,
    ) {
        val state = _uiState.value

        if (artistId == state.originalArtistId) {
            return
        }

        val exists =
            state.positions.any { position ->
                position.artist.id == artistId
            }

        if (!exists) {
            return
        }

        _uiState.update {
            it.copy(
                selectedPositionArtistId = artistId,
            )
        }
    }

    fun onStartChallengeClick() {
        if (_uiState.value.selectedPosition == null) {
            return
        }

        _uiState.update {
            it.copy(
                stage = CaptureStage.Countdown,
                countdown = COUNTDOWN_SECONDS,
                elapsedTimeMs = 0L,
            )
        }
    }

    /**
     * Camera와 영상 첫 프레임이 모두 준비된 이후 호출한다.
     */
    fun onShootingReady() {
        if (_uiState.value.stage != CaptureStage.Countdown) {
            return
        }

        if (countdownJob?.isActive == true) {
            return
        }

        countdownJob =
            viewModelScope.launch {
                for (count in COUNTDOWN_SECONDS downTo 1) {
                    _uiState.update {
                        it.copy(
                            countdown = count,
                        )
                    }

                    delay(1_000L)
                }

                _uiState.update {
                    it.copy(
                        stage = CaptureStage.Recording,
                        countdown = 0,
                    )
                }
            }
    }

    fun onRecordingProgress(
        elapsedTimeMs: Long,
    ) {
        _uiState.update {
            it.copy(
                elapsedTimeMs = elapsedTimeMs,
            )
        }
    }

    override fun onCleared() {
        countdownJob?.cancel()
        super.onCleared()
    }

    private companion object {
        const val COUNTDOWN_SECONDS = 5
    }
}