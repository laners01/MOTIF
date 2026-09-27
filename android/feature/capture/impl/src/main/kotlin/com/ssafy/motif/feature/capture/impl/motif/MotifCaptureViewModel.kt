package com.ssafy.motif.feature.capture.impl.motif

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.motif.core.common.result.Result
import com.ssafy.motif.core.common.result.asResult
import com.ssafy.motif.core.data.repository.MotifRepository
import com.ssafy.motif.feature.capture.impl.motif.model.MotifCaptureProcessingState
import com.ssafy.motif.feature.capture.impl.motif.model.MotifCaptureStage
import com.ssafy.motif.feature.capture.impl.motif.model.MotifCaptureUiEffect
import com.ssafy.motif.feature.capture.impl.motif.model.MotifCaptureUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

@HiltViewModel
internal class MotifCaptureViewModel @Inject constructor(
    private val motifRepository: MotifRepository,
) : ViewModel() {

    private val _uiState =
        MutableStateFlow(
            MotifCaptureUiState(),
        )

    val uiState: StateFlow<MotifCaptureUiState> =
        _uiState.asStateFlow()

    private val _uiEffect =
        MutableSharedFlow<MotifCaptureUiEffect>(
            extraBufferCapacity = 1,
        )

    val uiEffect: SharedFlow<MotifCaptureUiEffect> =
        _uiEffect.asSharedFlow()

    private var initializedAssetId: Long? = null
    private var assetLoadJob: Job? = null
    private var recommendationJob: Job? = null
    private var countdownJob: Job? = null
    private var submitJob: Job? = null

    private var recordedVideoFile: File? = null

    fun initialize(
        assetId: Long,
    ) {
        if (initializedAssetId == assetId) {
            return
        }

        initializedAssetId = assetId

        recordedVideoFile
            ?.delete()

        recordedVideoFile = null

        _uiState.value =
            MotifCaptureUiState(
                assetId = assetId,
                isLoading = true,
            )

        loadAsset(
            assetId = assetId,
        )

        loadRecommendations(
            assetId = assetId,
        )
    }

    fun startCapture() {
        val state =
            _uiState.value

        if (
            state.stage != MotifCaptureStage.Ready ||
            state.asset == null
        ) {
            return
        }

        countdownJob?.cancel()

        _uiState.update {
            it.copy(
                stage = MotifCaptureStage.Countdown,
                countdown = COUNTDOWN_SECONDS,
                elapsedTimeMs = 0L,
            )
        }
    }

    fun onShootingReady() {
        if (
            _uiState.value.stage !=
            MotifCaptureStage.Countdown
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
                    count in COUNTDOWN_SECONDS downTo 1
                ) {
                    _uiState.update { state ->
                        if (
                            state.stage !=
                            MotifCaptureStage.Countdown
                        ) {
                            return@update state
                        }

                        state.copy(
                            countdown = count,
                        )
                    }

                    delay(
                        COUNTDOWN_INTERVAL_MS.milliseconds,
                    )
                }

                _uiState.update { state ->
                    if (
                        state.stage !=
                        MotifCaptureStage.Countdown
                    ) {
                        return@update state
                    }

                    state.copy(
                        stage = MotifCaptureStage.Recording,
                        countdown = COUNTDOWN_SECONDS,
                        elapsedTimeMs = 0L,
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
                MotifCaptureStage.Recording
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

        recordedVideoFile
            ?.takeIf {
                it.absolutePath !=
                    videoFile.absolutePath
            }
            ?.delete()

        recordedVideoFile =
            videoFile

        _uiState.update { state ->
            state.copy(
                stage = MotifCaptureStage.Review,
                elapsedTimeMs = state.durationMs,
            )
        }
    }

    /**
     * Recording 도중 재촬영 버튼을 눌렀을 때 CameraX가
     * finalize된 파일은 ShootingScreen에서 제거한다.
     */
    fun onRecordingRetryReady() {
        countdownJob?.cancel()

        _uiState.update { state ->
            state.copy(
                stage = MotifCaptureStage.Ready,
                countdown = COUNTDOWN_SECONDS,
                elapsedTimeMs = 0L,
            )
        }
    }

    fun retryRecording() {
        recordedVideoFile
            ?.delete()

        recordedVideoFile = null

        countdownJob?.cancel()

        _uiState.update { state ->
            state.copy(
                stage = MotifCaptureStage.Ready,
                countdown = COUNTDOWN_SECONDS,
                elapsedTimeMs = 0L,
                processingState =
                    MotifCaptureProcessingState.Idle,
                uploadProgress = INITIAL_UPLOAD_PROGRESS,
                jobUuid = null,
            )
        }
    }

    fun makeMotif() {
        val state =
            _uiState.value

        val videoFile =
            recordedVideoFile
                ?: run {
                    emitError(
                        IllegalStateException(
                            "Recorded video file is missing.",
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
                stage = MotifCaptureStage.Processing,
                processingState =
                    MotifCaptureProcessingState.PreparingUpload,
                uploadProgress = INITIAL_UPLOAD_PROGRESS,
            )
        }

        submitJob =
            viewModelScope.launch {
                try {
                    val upload =
                        motifRepository
                            .createMotifCompositeJob(
                                assetId = state.assetId,
                                contentLength =
                                    videoFile.length(),
                            )
                            .first()

                    _uiState.update {
                        it.copy(
                            jobUuid = upload.jobUuid,
                            processingState =
                                MotifCaptureProcessingState.Uploading,
                            uploadProgress =
                                INITIAL_UPLOAD_PROGRESS,
                        )
                    }

                    motifRepository
                        .uploadMotifCompositeVideo(
                            uploadUrl = upload.uploadUrl,
                            videoFile = videoFile,
                        )
                        .collect { progress ->
                            _uiState.update {
                                it.copy(
                                    processingState =
                                        MotifCaptureProcessingState.Uploading,
                                    uploadProgress =
                                        progress.coerceIn(
                                            MIN_UPLOAD_PROGRESS,
                                            MAX_UPLOAD_PROGRESS,
                                        ),
                                )
                            }
                        }

                    _uiState.update {
                        it.copy(
                            processingState =
                                MotifCaptureProcessingState
                                    .RequestingComposite,
                            uploadProgress =
                                MAX_UPLOAD_PROGRESS,
                        )
                    }

                    // MOTIF는 correctionEnabled를 보내지 않는다.
                    motifRepository
                        .requestMotifCompositeJob(
                            jobUuid = upload.jobUuid,
                        )
                        .first()

                    recordedVideoFile
                        ?.delete()

                    recordedVideoFile = null

                    _uiState.update {
                        it.copy(
                            processingState =
                                MotifCaptureProcessingState.Queued,
                            uploadProgress =
                                MAX_UPLOAD_PROGRESS,
                        )
                    }
                } catch (
                    throwable: Throwable,
                ) {
                    if (
                        throwable is
                        CancellationException
                    ) {
                        throw throwable
                    }

                    _uiState.update {
                        it.copy(
                            processingState =
                                MotifCaptureProcessingState.Failed,
                        )
                    }

                    emitError(
                        throwable,
                    )
                }
            }
    }

    fun retryTransfer() {
        if (
            _uiState.value.processingState !=
            MotifCaptureProcessingState.Failed
        ) {
            return
        }

        makeMotif()
    }

    fun onError(
        throwable: Throwable,
    ) {
        emitError(
            throwable,
        )
    }

    private fun loadAsset(
        assetId: Long,
    ) {
        assetLoadJob?.cancel()

        assetLoadJob =
            motifRepository
                .getAssetDetail(
                    assetId = assetId,
                )
                .asResult()
                .onEach { result ->
                    when (result) {
                        Result.Loading -> {
                            _uiState.update {
                                it.copy(
                                    isLoading = true,
                                )
                            }
                        }

                        is Result.Success -> {
                            val asset =
                                result.data

                            if (!asset.isAvailable) {
                                _uiState.update {
                                    it.copy(
                                        isLoading = false,
                                    )
                                }

                                emitError(
                                    IllegalStateException(
                                        "MOTIF asset access is required before capture.",
                                    ),
                                )

                                return@onEach
                            }

                            if (
                                asset.previewVideoUrl.isBlank() ||
                                asset.durationMs <= 0L
                            ) {
                                _uiState.update {
                                    it.copy(
                                        isLoading = false,
                                    )
                                }

                                emitError(
                                    IllegalStateException(
                                        "MOTIF asset capture source is invalid.",
                                    ),
                                )

                                return@onEach
                            }

                            _uiState.update {
                                it.copy(
                                    asset = asset,
                                    isLoading = false,
                                    stage =
                                        MotifCaptureStage.Ready,
                                )
                            }
                        }

                        is Result.Error -> {
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                )
                            }

                            emitError(
                                result.exception,
                            )
                        }
                    }
                }
                .launchIn(
                    viewModelScope,
                )
    }

    private fun loadRecommendations(
        assetId: Long,
    ) {
        recommendationJob?.cancel()

        recommendationJob =
            motifRepository
                .getShop()
                .onEach { sections ->
                    val assets =
                        sections
                            .flatMap { section ->
                                section.assets
                            }
                            .filter { asset ->
                                asset.id != assetId
                            }
                            .take(RECOMMENDATION_COUNT)

                    _uiState.update {
                        it.copy(
                            recommendedAssets = assets,
                        )
                    }
                }
                .catch {
                    // 추천 목록은 촬영 필수 데이터가 아니므로
                    // 실패해도 촬영 흐름은 유지한다.
                }
                .launchIn(
                    viewModelScope,
                )
    }

    private fun emitError(
        throwable: Throwable,
    ) {
        _uiEffect.tryEmit(
            MotifCaptureUiEffect.Error(
                throwable = throwable,
            ),
        )
    }

    override fun onCleared() {
        countdownJob?.cancel()

        recordedVideoFile
            ?.delete()

        recordedVideoFile = null

        super.onCleared()
    }

    private companion object {
        const val COUNTDOWN_SECONDS = 5
        const val COUNTDOWN_INTERVAL_MS = 1_000L

        const val MIN_UPLOAD_PROGRESS = 0
        const val MAX_UPLOAD_PROGRESS = 100
        const val INITIAL_UPLOAD_PROGRESS = 0

        const val RECOMMENDATION_COUNT = 3
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
