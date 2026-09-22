package com.ssafy.motif.feature.motif.impl.assets

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import com.ssafy.motif.core.model.motif.Asset
import com.ssafy.motif.core.model.artist.Artist

@HiltViewModel
class AssetListViewModel @Inject constructor(): ViewModel() {

    private val _uiState = MutableStateFlow<AssetListUiState>(AssetListUiState.Loading)
    internal val uiState: StateFlow<AssetListUiState> = _uiState.asStateFlow()

    private val availableAssets: List<Asset> =  AssetListMockData.assets.filter { it.isAvailable }

    private val filterArtists:List<Artist> = availableAssets.map{it.artist}.distinctBy{it.id}

    init {
        loadFirstPage(selectedArtistId = null)
    }

    fun selectArtist(artistId: Long?) {
        val current = _uiState.value as? AssetListUiState.Success
        if (current?.selectedArtistId == artistId) return   // 같은 칩 재클릭은 무시

        loadFirstPage(selectedArtistId = artistId)
    }

    fun loadMore() {
        val current = _uiState.value as? AssetListUiState.Success ?: return
        if (!current.hasNext) return
        if (current.appendState !is AssetListAppendUiState.Idle) return

        viewModelScope.launch {
            _uiState.update {
                current.copy(appendState = AssetListAppendUiState.Loading)
            }

            val source = assetsOf(current.selectedArtistId)
            val loaded = current.assets.size
            val next = source.drop(loaded).take(PAGE_SIZE)

            _uiState.update {
                current.copy(
                    assets = current.assets + next,
                    hasNext = loaded + next.size < source.size,
                    appendState = AssetListAppendUiState.Idle,
                )
            }
        }
    }

    fun selectAsset(assetId: Long) {
        // TODO 에셋 바텀시트 노출 (별도 티켓)
    }

    private fun loadFirstPage(selectedArtistId: Long?) {
        viewModelScope.launch {
            _uiState.update { AssetListUiState.Loading }

            val source = assetsOf(selectedArtistId)
            val firstPage = source.take(PAGE_SIZE)

            _uiState.update {
                AssetListUiState.Success(
                    assets = firstPage,
                    artists = filterArtists,
                    selectedArtistId = selectedArtistId,
                    hasNext = firstPage.size < source.size,
                )
            }
        }
    }

    private fun assetsOf(artistId: Long?): List<Asset> =
        if (artistId == null) availableAssets
        else availableAssets.filter { it.artist.id == artistId }

    private companion object {
        const val PAGE_SIZE = 8
    }
}