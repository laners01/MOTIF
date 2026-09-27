package com.ssafy.motif.feature.motif.impl.shop.pass

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

@HiltViewModel
internal class PassPurchaseViewModel @Inject constructor(
    private val motifRepository: MotifRepository,
    private val artistRepository: ArtistRepository,
) : ViewModel() {

    private val _uiState =
        MutableStateFlow<PassPurchaseUiState>(
            PassPurchaseUiState.Loading,
        )

    internal val uiState:
            StateFlow<PassPurchaseUiState> =
        _uiState.asStateFlow()

    private var loadJob: Job? = null

    fun load(
        artistId: Long,
    ) {
        loadJob?.cancel()

        loadJob =
            combine(
                artistRepository.getArtistDetail(
                    id = artistId,
                ),
                motifRepository.getShop(),
                motifRepository.getArtistMotifPass(
                    artistId = artistId,
                ),
            ) {
                    artistDetail,
                    shopSections,
                    pass,
                ->

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

                PassPurchaseUiState.Success(
                    detail =
                        ShopArtistDetail(
                            artist =
                                artistDetail.artist,
                            introduction =
                                artistDetail.introduction,
                            assets =
                                section.assets,
                            pass = pass,
                            hasPass =
                                pass.isActive,
                        ),
                )
            }
                .asResult()
                .onEach { result ->
                    _uiState.value =
                        when (result) {
                            Result.Loading -> {
                                PassPurchaseUiState
                                    .Loading
                            }

                            is Result.Success -> {
                                result.data
                            }

                            is Result.Error -> {
                                PassPurchaseUiState
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

    fun purchasePlan(
        planId: Long,
    ) {
        val current =
            _uiState.value as?
                    PassPurchaseUiState.Success
                ?: return

        val plan =
            current.detail
                .pass
                .plans
                .firstOrNull {
                        plan,
                    ->
                    plan.id ==
                            planId
                }
                ?: return

        // TODO Payment API 연동
        // plan.months
        // plan.totalPrice
        // plan.currency
    }
}