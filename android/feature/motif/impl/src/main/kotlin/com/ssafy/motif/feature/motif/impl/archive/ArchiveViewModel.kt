package com.ssafy.motif.feature.motif.impl.archive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.motif.core.model.home.Artist
import com.ssafy.motif.core.model.motif.SavedMotif
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ArchiveViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow<ArchiveUiState>(ArchiveUiState.Loading)
    internal val uiState: StateFlow<ArchiveUiState> = _uiState.asStateFlow()

    // TODO Repository 도입 시 repository의 Flow로 대체된다.
    private val sourceMotifs: List<SavedMotif> = ArchiveMockData.savedMotifs

    // 저장 결과물이 있는 아티스트만, 최근 저장순으로.
    private val filterArtists: List<Artist>
        get() = sourceMotifs.map { it.artist }.distinctBy { it.id }

    init {
        loadFirstPage(
            selectedArtistId = null,
            selectedMotif = defaultSelection(),
        )
    }

    /**
     * 아티스트 필터를 선택한다. 이미 선택된 아티스트를 다시 누르면 해제되어 전체로 돌아간다.
     * 재생 중인 모티프는 필터와 무관하게 유지한다.
     */
    fun selectArtist(artistId: Long) {
        val current = _uiState.value as? ArchiveUiState.Success ?: return
        val nextArtistId = if (current.selectedArtistId == artistId) null else artistId

        loadFirstPage(
            selectedArtistId = nextArtistId,
            selectedMotif = current.selectedMotif,
        )
    }

    /**
     * 목록 항목을 선택해 상단 재생 대상으로 지정한다.
     */
    fun selectMotif(motifId: Long) {
        val motif = sourceMotifs.firstOrNull { it.id == motifId } ?: return

        _uiState.update { state ->
            val current = state as? ArchiveUiState.Success ?: return@update state
            current.copy(selectedMotif = motif)
        }
    }

    fun loadMore() {
        val current = _uiState.value as? ArchiveUiState.Success ?: return
        if (!current.hasNext) return
        if (current.appendState !is ArchiveAppendUiState.Idle) return

        viewModelScope.launch {
            _uiState.update {
                current.copy(appendState = ArchiveAppendUiState.Loading)
            }

            val source = motifsOf(current.selectedArtistId)
            val loaded = current.motifs.size
            val next = source.drop(loaded).take(PAGE_SIZE)

            _uiState.update {
                current.copy(
                    motifs = current.motifs + next,
                    hasNext = loaded + next.size < source.size,
                    appendState = ArchiveAppendUiState.Idle,
                )
            }
        }
    }

    private fun loadFirstPage(
        selectedArtistId: Long?,
        selectedMotif: SavedMotif?,
    ) {
        viewModelScope.launch {
            _uiState.update { ArchiveUiState.Loading }

            val source = motifsOf(selectedArtistId)
            val firstPage = source.take(PAGE_SIZE)

            _uiState.update {
                ArchiveUiState.Success(
                    motifs = firstPage,
                    artists = filterArtists,
                    selectedArtistId = selectedArtistId,
                    // 이전 선택이 없으면 필터된 첫 항목을 재생한다.
                    selectedMotif = selectedMotif ?: firstPage.firstOrNull(),
                    hasNext = firstPage.size < source.size,
                )
            }
        }
    }

    /**
     * 진입 시 기본 재생 대상. 대표 모티프가 있으면 그것, 없으면 가장 최근 저장물.
     */
    private fun defaultSelection(): SavedMotif? =
        sourceMotifs.firstOrNull { it.isRepresentative } ?: sourceMotifs.firstOrNull()

    private fun motifsOf(artistId: Long?): List<SavedMotif> =
        if (artistId == null) sourceMotifs
        else sourceMotifs.filter { it.artist.id == artistId }

    private companion object {
        const val PAGE_SIZE = 6
    }
}
