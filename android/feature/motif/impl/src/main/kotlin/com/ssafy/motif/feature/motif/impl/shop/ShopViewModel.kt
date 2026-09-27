package com.ssafy.motif.feature.motif.impl.shop

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.motif.core.common.result.Result
import com.ssafy.motif.core.common.result.asResult
import com.ssafy.motif.core.data.repository.MotifRepository
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
internal class ShopViewModel @Inject constructor(
    private val motifRepository: MotifRepository,
) : ViewModel() {

    private val _uiState =
        MutableStateFlow<ShopUiState>(
            ShopUiState.Loading,
        )

    internal val uiState:
            StateFlow<ShopUiState> =
        _uiState.asStateFlow()

    private var loadJob: Job? = null

    init {
        loadShop()
    }

    fun retry() {
        loadShop()
    }

    private fun loadShop() {
        loadJob?.cancel()

        loadJob =
            combine(
                motifRepository.getShop(),
                motifRepository.getShopBanners(),
            ) {
                    sections,
                    banners,
                ->
                ShopUiState.Success(
                    banners = banners,
                    sections = sections,
                )
            }
                .asResult()
                .onEach { result ->
                    _uiState.value =
                        when (result) {
                            Result.Loading -> {
                                ShopUiState.Loading
                            }

                            is Result.Success -> {
                                result.data
                            }

                            is Result.Error -> {
                                ShopUiState.Error(
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
}