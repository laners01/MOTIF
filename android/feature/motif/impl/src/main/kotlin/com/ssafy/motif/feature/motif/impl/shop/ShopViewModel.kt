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

    private var loadJob: Job? =
        null

    fun refresh() {
        loadShop()
    }

    fun retry() {
        refresh()
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
                    when (result) {
                        Result.Loading -> {
                            if (
                                _uiState.value !is
                                        ShopUiState.Success
                            ) {
                                _uiState.value =
                                    ShopUiState.Loading
                            }
                        }

                        is Result.Success -> {
                            _uiState.value =
                                result.data
                        }

                        is Result.Error -> {
                            if (
                                _uiState.value !is
                                        ShopUiState.Success
                            ) {
                                _uiState.value =
                                    ShopUiState.Error(
                                        exception =
                                            result.exception,
                                    )
                            }
                        }
                    }
                }
                .launchIn(
                    viewModelScope,
                )
    }
}