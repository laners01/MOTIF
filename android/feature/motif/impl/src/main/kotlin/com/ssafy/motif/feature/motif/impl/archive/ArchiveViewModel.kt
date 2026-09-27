package com.ssafy.motif.feature.motif.impl.archive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.motif.core.common.result.Result
import com.ssafy.motif.core.common.result.asResult
import com.ssafy.motif.core.data.repository.MotifRepository
import com.ssafy.motif.core.model.artist.Artist
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update

@HiltViewModel
internal class ArchiveViewModel @Inject constructor(
    private val motifRepository: MotifRepository,
) : ViewModel() {

    private val _uiState =
        MutableStateFlow<ArchiveUiState>(
            ArchiveUiState.Loading,
        )

    internal val uiState:
            StateFlow<ArchiveUiState> =
        _uiState.asStateFlow()

    /**
     * 서버에는 "내 보관함에 결과가 존재하는 아티스트 목록"
     * 전용 API가 없으므로 지금까지 읽은 결과에서 누적한다.
     */
    private val knownArtists =
        linkedMapOf<Long, Artist>()

    private var pageJob: Job? = null
    private var detailJob: Job? = null
    private var representativeJob: Job? = null

    init {
        loadFirstPage(
            selectedArtistId = null,
            preserveSelectedMotif = false,
        )
    }

    fun retry() {
        val current =
            _uiState.value as?
                    ArchiveUiState.Success

        loadFirstPage(
            selectedArtistId =
                current?.selectedArtistId,
            preserveSelectedMotif =
                false,
        )
    }

    fun selectArtist(
        artistId: Long,
    ) {
        val current =
            _uiState.value as?
                    ArchiveUiState.Success
                ?: return

        val nextArtistId =
            if (
                current.selectedArtistId ==
                artistId
            ) {
                null
            } else {
                artistId
            }

        loadFirstPage(
            selectedArtistId =
                nextArtistId,
            preserveSelectedMotif =
                true,
        )
    }

    fun selectMotif(
        motifId: Long,
    ) {
        loadMotifDetail(
            motifId = motifId,
        )
    }

    fun toggleRepresentative(
        motifId: Long,
    ) {
        val current =
            _uiState.value as?
                    ArchiveUiState.Success
                ?: return

        if (
            current
                .updatingRepresentativeMotifId !=
            null
        ) {
            return
        }

        val target =
            current.motifs
                .firstOrNull { motif ->
                    motif.id ==
                            motifId
                }
                ?: return

        val makeRepresentative =
            !target.isRepresentative

        representativeJob?.cancel()

        representativeJob =
            motifRepository
                .updateMotifResult(
                    resultId =
                        motifId,
                    isRepresentative =
                        makeRepresentative,
                )
                .asResult()
                .onEach { result ->
                    when (result) {
                        Result.Loading -> {
                            _uiState.update {
                                    state,
                                ->
                                val success =
                                    state as?
                                            ArchiveUiState
                                            .Success
                                        ?: return@update state

                                success.copy(
                                    updatingRepresentativeMotifId =
                                        motifId,
                                )
                            }
                        }

                        is Result.Success -> {
                            val updated =
                                result.data

                            _uiState.update {
                                    state,
                                ->
                                val success =
                                    state as?
                                            ArchiveUiState
                                            .Success
                                        ?: return@update state

                                val motifs =
                                    success.motifs
                                        .map {
                                                motif,
                                            ->
                                            when {
                                                motif.id ==
                                                        updated.id -> {
                                                    motif.copy(
                                                        isRepresentative =
                                                            updated
                                                                .isRepresentative,
                                                    )
                                                }

                                                makeRepresentative -> {
                                                    motif.copy(
                                                        isRepresentative =
                                                            false,
                                                    )
                                                }

                                                else ->
                                                    motif
                                            }
                                        }

                                val selected =
                                    success
                                        .selectedMotif
                                        ?.let {
                                                selectedMotif,
                                            ->
                                            when {
                                                selectedMotif.id ==
                                                        updated.id -> {
                                                    updated
                                                }

                                                makeRepresentative &&
                                                        selectedMotif
                                                            .isRepresentative -> {
                                                    selectedMotif.copy(
                                                        isRepresentative =
                                                            false,
                                                    )
                                                }

                                                else -> {
                                                    selectedMotif
                                                }
                                            }
                                        }

                                success.copy(
                                    motifs = motifs,
                                    selectedMotif =
                                        selected,
                                    updatingRepresentativeMotifId =
                                        null,
                                )
                            }
                        }

                        is Result.Error -> {
                            _uiState.update {
                                    state,
                                ->
                                val success =
                                    state as?
                                            ArchiveUiState
                                            .Success
                                        ?: return@update state

                                success.copy(
                                    updatingRepresentativeMotifId =
                                        null,
                                )
                            }
                        }
                    }
                }
                .launchIn(
                    viewModelScope,
                )
    }

    fun loadMore() {
        val current =
            _uiState.value as?
                    ArchiveUiState.Success
                ?: return

        if (
            !current.hasNext ||
            current.nextCursor == null ||
            current.appendState !is
                    ArchiveAppendUiState.Idle
        ) {
            return
        }

        pageJob?.cancel()

        _uiState.update {
            current.copy(
                appendState =
                    ArchiveAppendUiState
                        .Loading,
            )
        }

        pageJob =
            motifRepository
                .getMotifResults(
                    cursor =
                        current.nextCursor,
                    size =
                        PAGE_SIZE,
                    artistId =
                        current.selectedArtistId,
                )
                .asResult()
                .onEach { result ->
                    when (result) {
                        Result.Loading -> Unit

                        is Result.Success -> {
                            val page =
                                result.data

                            rememberArtists(
                                artists =
                                    page.items
                                        .map {
                                                motif,
                                            ->
                                            motif.artist
                                        },
                            )

                            _uiState.update {
                                    state,
                                ->
                                val success =
                                    state as?
                                            ArchiveUiState
                                            .Success
                                        ?: return@update state

                                success.copy(
                                    motifs =
                                        (
                                                success.motifs +
                                                        page.items
                                                )
                                            .distinctBy {
                                                    motif,
                                                ->
                                                motif.id
                                            },
                                    artists =
                                        knownArtists
                                            .values
                                            .toList(),
                                    nextCursor =
                                        page.nextCursor,
                                    hasNext =
                                        page.hasNext,
                                    appendState =
                                        ArchiveAppendUiState
                                            .Idle,
                                )
                            }
                        }

                        is Result.Error -> {
                            _uiState.update {
                                    state,
                                ->
                                val success =
                                    state as?
                                            ArchiveUiState
                                            .Success
                                        ?: return@update state

                                success.copy(
                                    appendState =
                                        ArchiveAppendUiState
                                            .Error(
                                                exception =
                                                    result
                                                        .exception,
                                            ),
                                )
                            }
                        }
                    }
                }
                .launchIn(
                    viewModelScope,
                )
    }

    private fun loadFirstPage(
        selectedArtistId: Long?,
        preserveSelectedMotif: Boolean,
    ) {
        pageJob?.cancel()

        val previousSelectedMotif =
            if (
                preserveSelectedMotif
            ) {
                (
                        _uiState.value as?
                                ArchiveUiState.Success
                        )
                    ?.selectedMotif
            } else {
                null
            }

        pageJob =
            motifRepository
                .getMotifResults(
                    cursor = null,
                    size =
                        PAGE_SIZE,
                    artistId =
                        selectedArtistId,
                )
                .asResult()
                .onEach { result ->
                    when (result) {
                        Result.Loading -> {
                            _uiState.value =
                                ArchiveUiState
                                    .Loading
                        }

                        is Result.Success -> {
                            val page =
                                result.data

                            rememberArtists(
                                artists =
                                    page.items
                                        .map {
                                                motif,
                                            ->
                                            motif.artist
                                        },
                            )

                            _uiState.value =
                                ArchiveUiState
                                    .Success(
                                        motifs =
                                            page.items,
                                        artists =
                                            knownArtists
                                                .values
                                                .toList(),
                                        selectedArtistId =
                                            selectedArtistId,
                                        selectedMotif =
                                            previousSelectedMotif,
                                        nextCursor =
                                            page.nextCursor,
                                        hasNext =
                                            page.hasNext,
                                    )

                            if (
                                previousSelectedMotif ==
                                null
                            ) {
                                val defaultMotif =
                                    page.items
                                        .firstOrNull {
                                                motif,
                                            ->
                                            motif.isRepresentative
                                        }
                                        ?: page.items
                                            .firstOrNull()

                                defaultMotif
                                    ?.let {
                                            motif,
                                        ->
                                        loadMotifDetail(
                                            motifId =
                                                motif.id,
                                        )
                                    }
                            }
                        }

                        is Result.Error -> {
                            _uiState.value =
                                ArchiveUiState
                                    .Error(
                                        exception =
                                            result.exception,
                                    )
                        }
                    }
                }
                .launchIn(
                    viewModelScope,
                )
    }

    private fun loadMotifDetail(
        motifId: Long,
    ) {
        detailJob?.cancel()

        detailJob =
            motifRepository
                .getMotifResultDetail(
                    resultId =
                        motifId,
                )
                .asResult()
                .onEach { result ->
                    if (
                        result !is
                                Result.Success
                    ) {
                        return@onEach
                    }

                    val motif =
                        result.data

                    rememberArtists(
                        artists =
                            listOf(
                                motif.artist,
                            ),
                    )

                    _uiState.update {
                            state,
                        ->
                        val success =
                            state as?
                                    ArchiveUiState
                                    .Success
                                ?: return@update state

                        success.copy(
                            motifs =
                                success.motifs
                                    .map {
                                            item,
                                        ->
                                        if (
                                            item.id ==
                                            motif.id
                                        ) {
                                            item.copy(
                                                isRepresentative =
                                                    motif
                                                        .isRepresentative,
                                            )
                                        } else {
                                            item
                                        }
                                    },
                            artists =
                                knownArtists
                                    .values
                                    .toList(),
                            selectedMotif =
                                motif,
                        )
                    }
                }
                .launchIn(
                    viewModelScope,
                )
    }

    private fun rememberArtists(
        artists: List<Artist>,
    ) {
        artists.forEach { artist ->
            knownArtists[
                artist.id
            ] = artist
        }
    }

    private companion object {
        const val PAGE_SIZE = 20
    }
}