package com.ssafy.motif.feature.capture.impl.challenge

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.motif.core.common.result.Result
import com.ssafy.motif.core.common.result.asResult
import com.ssafy.motif.core.data.repository.ChallengeRepository
import com.ssafy.motif.feature.capture.impl.challenge.model.CaptureStage
import com.ssafy.motif.feature.capture.impl.challenge.model.CaptureUiEffect
import com.ssafy.motif.feature.capture.impl.challenge.model.CaptureUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
internal class CaptureViewModel @Inject constructor(
    private val challengeRepository: ChallengeRepository,
) : ViewModel() {

    private val _uiState =
        MutableStateFlow(
            CaptureUiState(),
        )

    val uiState: StateFlow<CaptureUiState> =
        _uiState.asStateFlow()

    private val _uiEffect =
        MutableSharedFlow<CaptureUiEffect>(
            extraBufferCapacity = 1,
        )

    val uiEffect: SharedFlow<CaptureUiEffect> =
        _uiEffect.asSharedFlow()

    private var initializedChallengeId: Long? =
        null

    private var positionLoadJob: Job? =
        null

    private var guideVideoLoadJob: Job? =
        null

    private var countdownJob: Job? =
        null

    private var submitJob: Job? =
        null

    fun initialize(
        challengeId: Long,
        challengeThumbnailUrl: String,
    ) {
        if (
            initializedChallengeId ==
            challengeId
        ) {
            return
        }

        initializedChallengeId =
            challengeId

        _uiState.value =
            CaptureUiState(
                challengeId =
                    challengeId,
                challengeThumbnailUrl =
                    challengeThumbnailUrl,
                isLoading =
                    true,
            )

        loadCapturePositions(
            challengeId =
                challengeId,
        )
    }

    fun selectPosition(
        participantId: Long,
    ) {
        val position =
            _uiState.value
                .positions
                .firstOrNull { position ->
                    position.id ==
                            participantId
                }
                ?: return

        if (!position.isAvailable) {
            return
        }

        _uiState.update { state ->
            state.copy(
                selectedParticipantId =
                    participantId,
            )
        }
    }

    fun startChallenge() {
        val state =
            _uiState.value

        val position =
            state.selectedPosition
                ?: return

        if (
            !position.isAvailable ||
            state.isPreparingShooting
        ) {
            return
        }

        guideVideoLoadJob?.cancel()

        guideVideoLoadJob =
            challengeRepository
                .getChallengeCaptureVideo(
                    participantId =
                        position.id,
                )
                .asResult()
                .onEach { result ->
                    when (result) {
                        Result.Loading -> {
                            _uiState.update {
                                it.copy(
                                    isPreparingShooting =
                                        true,
                                )
                            }
                        }

                        is Result.Success -> {
                            val guideVideo =
                                result.data

                            if (
                                guideVideo.challengeId !=
                                state.challengeId
                            ) {
                                _uiState.update {
                                    it.copy(
                                        isPreparingShooting =
                                            false,
                                    )
                                }

                                emitError(
                                    IllegalStateException(
                                        "Capture guide challenge mismatch.",
                                    ),
                                )

                                return@onEach
                            }

                            _uiState.update {
                                it.copy(
                                    stage =
                                        CaptureStage.Countdown,
                                    countdown =
                                        COUNTDOWN_SECONDS,
                                    elapsedTimeMs =
                                        0L,
                                    guideVideoUrl =
                                        guideVideo.videoUrl,
                                    durationMs =
                                        guideVideo.durationMs,
                                    isPreparingShooting =
                                        false,
                                )
                            }
                        }

                        is Result.Error -> {
                            _uiState.update {
                                it.copy(
                                    isPreparingShooting =
                                        false,
                                )
                            }

                            emitError(
                                result.exception,
                            )
                        }
                    }
                }
                .launchIn(viewModelScope)
    }

    fun onShootingReady() {
        if (
            _uiState.value.stage !=
            CaptureStage.Countdown
        ) {
            return
        }

        if (
            countdownJob?.isActive ==
            true
        ) {
            return
        }

        countdownJob =
            viewModelScope.launch {
                for (
                count in
                COUNTDOWN_SECONDS downTo 1
                ) {
                    _uiState.update { state ->
                        state.copy(
                            countdown = count,
                        )
                    }

                    delay(
                        COUNTDOWN_INTERVAL_MS,
                    )
                }

                _uiState.update { state ->
                    if (
                        state.stage !=
                        CaptureStage.Countdown
                    ) {
                        return@update state
                    }

                    state.copy(
                        stage =
                            CaptureStage.Recording,
                        countdown =
                            COUNTDOWN_SECONDS,
                        elapsedTimeMs =
                            0L,
                    )
                }
            }
    }

    fun onRecordingProgress(
        elapsedTimeMs: Long,
    ) {
        _uiState.update { state ->
            if (
                state.stage !=
                CaptureStage.Recording
            ) {
                return@update state
            }

            state.copy(
                elapsedTimeMs =
                    elapsedTimeMs.coerceAtMost(
                        state.durationMs,
                    ),
            )
        }
    }

    fun onRecordingFinished(
        uri: Uri,
    ) {
        val state =
            _uiState.value

        val selectedPosition =
            state.selectedPosition
                ?: return

        val videoFile =
            uri.toVideoFile()
                ?: run {
                    emitError(
                        IllegalStateException(
                            "Recorded video file could not be resolved.",
                        ),
                    )

                    return
                }

        if (
            !videoFile.exists() ||
            videoFile.length() <= 0L
        ) {
            emitError(
                IllegalStateException(
                    "Recorded video file is invalid.",
                ),
            )

            return
        }

        if (
            submitJob?.isActive ==
            true
        ) {
            return
        }

        _uiState.update {
            it.copy(
                stage =
                    CaptureStage.Processing,
                elapsedTimeMs =
                    it.durationMs,
                isSubmitting =
                    true,
            )
        }

        submitJob =
            viewModelScope.launch {
                try {
                    val upload =
                        challengeRepository
                            .createChallengeCompositeJob(
                                challengeId =
                                    state.challengeId,
                                challengeParticipantId =
                                    selectedPosition.id,
                                contentLength =
                                    videoFile.length(),
                            )
                            .first()

                    challengeRepository
                        .uploadChallengeCompositeVideo(
                            uploadUrl =
                                upload.uploadUrl,
                            videoFile =
                                videoFile,
                        )
                        .first()

                    /*
                     * 현재 챌린지 합성은 보정을 항상 활성화한다.
                     * 추후 사용자 선택 기능이 추가되면
                     * UiState로 분리한다.
                     */
                    challengeRepository
                        .requestChallengeCompositeJob(
                            jobUuid =
                                upload.jobUuid,
                            correctionEnabled =
                                true,
                        )
                        .first()

                    _uiState.update {
                        it.copy(
                            jobUuid =
                                upload.jobUuid,
                            isSubmitting =
                                false,
                        )
                    }
                } catch (
                    throwable: Throwable,
                ) {
                    _uiState.update {
                        it.copy(
                            isSubmitting =
                                false,
                        )
                    }

                    emitError(
                        throwable,
                    )
                }
            }
    }

    fun onRetryReady() {
        countdownJob?.cancel()

        _uiState.update { state ->
            state.copy(
                stage =
                    CaptureStage.Countdown,
                countdown =
                    COUNTDOWN_SECONDS,
                elapsedTimeMs =
                    0L,
            )
        }
    }

    fun onError(
        throwable: Throwable,
    ) {
        emitError(
            throwable,
        )
    }

    private fun loadCapturePositions(
        challengeId: Long,
    ) {
        positionLoadJob?.cancel()

        positionLoadJob =
            challengeRepository
                .getChallengeCapturePositions(
                    challengeId =
                        challengeId,
                )
                .asResult()
                .onEach { result ->
                    when (result) {
                        Result.Loading -> {
                            _uiState.update {
                                it.copy(
                                    isLoading =
                                        true,
                                )
                            }
                        }

                        is Result.Success -> {
                            _uiState.update {
                                it.copy(
                                    positions =
                                        result.data,
                                    isLoading =
                                        false,
                                )
                            }
                        }

                        is Result.Error -> {
                            _uiState.update {
                                it.copy(
                                    isLoading =
                                        false,
                                )
                            }

                            emitError(
                                result.exception,
                            )
                        }
                    }
                }
                .launchIn(viewModelScope)
    }

    private fun emitError(
        throwable: Throwable,
    ) {
        _uiEffect.tryEmit(
            CaptureUiEffect.Error(
                throwable =
                    throwable,
            ),
        )
    }

    override fun onCleared() {
        countdownJob?.cancel()
        super.onCleared()
    }

    private companion object {
        const val COUNTDOWN_SECONDS = 5
        const val COUNTDOWN_INTERVAL_MS = 1_000L
    }
}

private fun Uri.toVideoFile(): File? {
    if (scheme != "file") {
        return null
    }

    val filePath =
        path
            ?: return null

    return File(
        filePath,
    )
}