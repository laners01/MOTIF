package com.ssafy.motif.feature.motif.impl.shop.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.motif.core.common.result.Result
import com.ssafy.motif.core.common.result.asResult
import com.ssafy.motif.core.data.repository.ArtistRepository
import com.ssafy.motif.core.data.repository.MotifRepository
import com.ssafy.motif.core.model.shop.ShopArtistDetail
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update

@HiltViewModel
internal class ProductDetailViewModel @Inject constructor(
    private val motifRepository: MotifRepository,
    private val artistRepository: ArtistRepository,
) : ViewModel() {

    private val _uiState =
        MutableStateFlow<ProductDetailUiState>(
            ProductDetailUiState.Loading,
        )

    internal val uiState:
            StateFlow<ProductDetailUiState> =
        _uiState.asStateFlow()

    private var loadJob: Job? = null
    private var assetJob: Job? = null

    fun load(
        artistId: Long,
        assetId: Long,
    ) {
        loadJob?.cancel()
        assetJob?.cancel()

        loadJob =
            combine(
                artistRepository.getArtistDetail(
                    id = artistId,
                ),
                motifRepository.getShop(),
                motifRepository.getArtistMotifPass(
                    artistId = artistId,
                ),
                motifRepository.getAssetDetail(
                    assetId = assetId,
                ),
            ) {
                    artistDetail,
                    shopSections,
                    pass,
                    selectedAsset,
                ->

                require(
                    selectedAsset.artist.id ==
                            artistId,
                ) {
                    "asset $assetId does not belong to artist $artistId"
                }

                val section =
                    requireNotNull(
                        shopSections
                            .firstOrNull {
                                    section,
                                ->
                                section.artist.id ==
                                        artistId
                            },
                    ) {
                        "artist $artistId does not exist in MOTIF shop"
                    }

                val assets =
                    section.assets
                        .map { asset ->
                            if (
                                asset.id ==
                                selectedAsset.id
                            ) {
                                selectedAsset
                            } else {
                                asset
                            }
                        }

                ProductDetailUiState.Success(
                    detail =
                        ShopArtistDetail(
                            artist =
                                artistDetail.artist,
                            introduction =
                                artistDetail.introduction,
                            assets = assets,
                            pass = pass,
                            hasPass =
                                pass.isActive,
                        ),
                    selectedAsset =
                        selectedAsset,
                )
            }
                .asResult()
                .onEach { result ->
                    _uiState.value =
                        when (result) {
                            Result.Loading -> {
                                ProductDetailUiState
                                    .Loading
                            }

                            is Result.Success -> {
                                result.data
                            }

                            is Result.Error -> {
                                ProductDetailUiState
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

    fun selectAsset(
        assetId: Long,
    ) {
        val current =
            _uiState.value as?
                    ProductDetailUiState.Success
                ?: return

        if (
            current.selectedAsset.id ==
            assetId
        ) {
            return
        }

        assetJob?.cancel()

        assetJob =
            motifRepository
                .getAssetDetail(
                    assetId = assetId,
                )
                .asResult()
                .onEach { result ->
                    if (
                        result !is Result.Success
                    ) {
                        return@onEach
                    }

                    val asset =
                        result.data

                    val latest =
                        _uiState.value as?
                                ProductDetailUiState
                                .Success
                            ?: return@onEach

                    if (
                        asset.artist.id !=
                        latest.detail.artist.id
                    ) {
                        return@onEach
                    }

                    _uiState.update {
                        latest.copy(
                            detail =
                                latest.detail.copy(
                                    assets =
                                        latest.detail
                                            .assets
                                            .map {
                                                    item,
                                                ->
                                                if (
                                                    item.id ==
                                                    asset.id
                                                ) {
                                                    asset
                                                } else {
                                                    item
                                                }
                                            },
                                ),
                            selectedAsset =
                                asset,
                        )
                    }
                }
                .launchIn(
                    viewModelScope,
                )
    }

    fun purchaseSelectedAsset() {
        val current =
            _uiState.value as?
                    ProductDetailUiState.Success
                ?: return

        if (
            current.isSelectedAssetPurchased
        ) {
            return
        }

        if (
            !current.selectedAsset
                .purchasable
        ) {
            return
        }

        // TODO Payment API 연동
    }
}