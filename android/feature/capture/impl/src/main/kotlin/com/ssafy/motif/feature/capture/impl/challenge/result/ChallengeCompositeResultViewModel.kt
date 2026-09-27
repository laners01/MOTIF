package com.ssafy.motif.feature.capture.impl.challenge.result

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.motif.core.data.repository.ChallengeRepository
import com.ssafy.motif.core.model.challenge.ParticipationVisibility
import com.ssafy.motif.core.model.composite.CompositeJobStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
internal class ChallengeCompositeResultViewModel @Inject constructor(
    private val challengeRepository:
    ChallengeRepository,
) : ViewModel() {

    private val _uiState =
        MutableStateFlow(
            ChallengeCompositeResultUiState(),
        )

    val uiState:
            StateFlow<ChallengeCompositeResultUiState> =
        _uiState.asStateFlow()

    private val _uiEffect =
        MutableSharedFlow<ChallengeCompositeResultEffect>(
            extraBufferCapacity = 1,
        )

    val uiEffect:
            SharedFlow<ChallengeCompositeResultEffect> =
        _uiEffect.asSharedFlow()

    private var initializedJobUuid:
            String? = null

    fun initialize(
        jobUuid: String,
    ) {
        if (
            initializedJobUuid ==
            jobUuid
        ) {
            return
        }

        initializedJobUuid =
            jobUuid

        _uiState.update {
            it.copy(
                jobUuid =
                    jobUuid,
            )
        }

        loadCompositeJob()
    }

    fun loadCompositeJob() {
        val jobUuid =
            _uiState.value
                .jobUuid
                .takeIf(String::isNotBlank)
                ?: return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    error = null,
                )
            }

            try {
                val compositeJob =
                    challengeRepository
                        .getChallengeCompositeJob(
                            jobUuid =
                                jobUuid,
                        )
                        .first()

                _uiState.update {
                    it.copy(
                        compositeJob =
                            compositeJob,
                        isLoading =
                            false,
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
                        isLoading = false,
                        error = throwable,
                    )
                }

                emitError(
                    throwable =
                        throwable,
                )
            }
        }
    }

    fun showUploadDialog() {
        if (
            _uiState.value
                .compositeJob
                ?.status !=
            CompositeJobStatus.DONE
        ) {
            return
        }

        _uiState.update {
            it.copy(
                dialog =
                    CompositeResultDialog.Upload,
            )
        }
    }

    fun showDeleteDialog() {
        _uiState.update {
            it.copy(
                dialog =
                    CompositeResultDialog.Delete,
            )
        }
    }

    fun dismissDialog() {
        _uiState.update {
            it.copy(
                dialog =
                    CompositeResultDialog.None,
            )
        }
    }

    fun updateDescription(
        description: String,
    ) {
        _uiState.update {
            it.copy(
                description =
                    description,
            )
        }
    }

    fun toggleVisibility() {
        _uiState.update {
            it.copy(
                isPublic =
                    !it.isPublic,
            )
        }
    }

    fun toggleCommentEnabled() {
        _uiState.update {
            it.copy(
                commentEnabled =
                    !it.commentEnabled,
            )
        }
    }

    fun publish() {
        val state =
            _uiState.value

        val job =
            state.compositeJob
                ?: return

        if (
            job.status !=
            CompositeJobStatus.DONE ||
            state.isPublishing
        ) {
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    dialog =
                        CompositeResultDialog.None,
                    isPublishing =
                        true,
                )
            }

            try {
                val videoId =
                    challengeRepository
                        .publishChallengeParticipation(
                            jobUuid =
                                state.jobUuid,
                            description =
                                state.description
                                    .trim(),
                            visibility =
                                if (
                                    state.isPublic
                                ) {
                                    ParticipationVisibility.PUBLIC
                                } else {
                                    ParticipationVisibility.PRIVATE
                                },
                            commentEnabled =
                                state.commentEnabled,
                        )
                        .first()

                _uiState.update {
                    it.copy(
                        isPublishing =
                            false,
                    )
                }

                _uiEffect.emit(
                    ChallengeCompositeResultEffect
                        .Published(
                            videoId =
                                videoId,
                        ),
                )
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
                        isPublishing =
                            false,
                    )
                }

                emitError(
                    throwable =
                        throwable,
                )
            }
        }
    }

    fun retryComposite() {
        val state =
            _uiState.value

        if (
            state.compositeJob
                ?.status !=
            CompositeJobStatus.FAILED
        ) {
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading =
                        true,
                )
            }

            try {
                val retryResult =
                    challengeRepository
                        .retryChallengeCompositeJob(
                            jobUuid =
                                state.jobUuid,
                        )
                        .first()

                _uiState.update { currentState ->
                    currentState.copy(
                        compositeJob =
                            currentState
                                .compositeJob
                                ?.copy(
                                    status =
                                        retryResult.status,
                                    attempt =
                                        retryResult.attempt,
                                    failCode =
                                        null,
                                ),
                        isLoading =
                            false,
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
                        isLoading =
                            false,
                    )
                }

                emitError(
                    throwable =
                        throwable,
                )
            }
        }
    }

    /*
     * 현재 Composite Job 삭제 API가 없으므로
     * 게시하지 않고 결과 화면에서 이탈한다.
     */
    fun discardResult() {
        _uiState.update {
            it.copy(
                dialog =
                    CompositeResultDialog.None,
            )
        }

        _uiEffect.tryEmit(
            ChallengeCompositeResultEffect.Close,
        )
    }

    private fun emitError(
        throwable: Throwable,
    ) {
        _uiEffect.tryEmit(
            ChallengeCompositeResultEffect.Error(
                throwable =
                    throwable,
            ),
        )
    }
}