package com.ssafy.motif.feature.motif.impl.assets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.motif.core.common.result.Result
import com.ssafy.motif.core.common.result.asResult
import com.ssafy.motif.core.data.repository.MotifRepository
import com.ssafy.motif.core.model.motif.Asset
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
internal class AssetListViewModel @Inject constructor(
    private val motifRepository: MotifRepository,
) : ViewModel() {

    private val _uiState =
        MutableStateFlow<AssetListUiState>(
            AssetListUiState.Loading,
        )

    internal val uiState:
            StateFlow<AssetListUiState> =
        _uiState.asStateFlow()

    private var allAssets: List<Asset> =
        emptyList()

    private var loadJob: Job? = null

    init {
        loadAssets()
    }

    fun retry() {
        loadAssets()
    }

    fun selectArtist(
        artistId: Long?,
    ) {
        val current =
            _uiState.value as? AssetListUiState.Success
                ?: return

        if (
            current.selectedArtistId ==
            artistId
        ) {
            return
        }

        _uiState.update {
            current.copy(
                assets = allAssets.filterByArtist(
                    artistId = artistId,
                ),
                selectedArtistId = artistId,
            )
        }
    }

    fun selectAsset(
        assetId: Long,
    ) {
        // TODO Asset 상세 BottomSheet 연결 시
        // motifRepository.getAssetDetail(assetId) 사용.
    }

    private fun loadAssets() {
        loadJob?.cancel()

        loadJob =
            motifRepository
                .getPurchasedAssets()
                .asResult()
                .onEach { result ->
                    _uiState.value =
                        when (result) {
                            Result.Loading -> {
                                AssetListUiState.Loading
                            }

                            is Result.Success -> {
                                allAssets =
                                    result.data

                                AssetListUiState.Success(
                                    assets =
                                        allAssets,
                                    artists =
                                        allAssets
                                            .map { asset ->
                                                asset.artist
                                            }
                                            .distinctBy { artist ->
                                                artist.id
                                            },
                                )
                            }

                            is Result.Error -> {
                                AssetListUiState.Error(
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

    private fun List<Asset>.filterByArtist(
        artistId: Long?,
    ): List<Asset> =
        if (artistId == null) {
            this
        } else {
            filter { asset ->
                asset.artist.id ==
                        artistId
            }
        }
}