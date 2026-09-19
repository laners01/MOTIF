package com.ssafy.motif.feature.motif.impl.shop

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.motif.core.model.motif.AssetAvailability
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ShopViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow<ShopUiState>(ShopUiState.Loading)
    internal val uiState: StateFlow<ShopUiState> = _uiState.asStateFlow()

    init {
        loadFirstPage()
    }

    fun selectBanner(bannerId: Long) {
        // TODO 구독권 상품 바텀시트 노출 (별도 티켓)
    }

    fun selectPass(artistId: Long) {
        // TODO PASS 결제 화면 이동 (별도 티켓)
    }

    fun loadMore() {
        val current = _uiState.value as? ShopUiState.Success ?: return
        if (!current.hasNext) return
        if (current.appendState !is ShopAppendUiState.Idle) return

        viewModelScope.launch {
            _uiState.update {
                current.copy(appendState = ShopAppendUiState.Loading)
            }

            val loaded = current.sections.size
            val next = ShopMockData.sections
                .drop(loaded)
                .take(PAGE_SIZE)

            _uiState.update {
                current.copy(
                    sections = current.sections + next,
                    hasNext = loaded + next.size < ShopMockData.sections.size,
                    appendState = ShopAppendUiState.Idle,
                )
            }
        }
    }

    /**
     * 구독권 구매 완료를 반영한다. 해당 아티스트 섹션의 P 뱃지가 켜지고, 잠긴 에셋이 구독권으로 열린다.
     *
     * TODO PurchaseRepository 도입 시 repository.purchasePass() 호출로 교체하고,
     *      상태 파생은 repository.purchases를 combine하는 쪽으로 옮긴다.
     */
    fun onPassPurchased(artistId: Long) {
        _uiState.update { state ->
            val current = state as? ShopUiState.Success ?: return@update state

            current.copy(
                sections = current.sections.map { section ->
                    if (section.artist.id != artistId) return@map section

                    section.copy(
                        hasPass = true,
                        assets = section.assets.map { asset ->
                            if (asset.availability == AssetAvailability.LOCKED) {
                                asset.copy(availability = AssetAvailability.SUBSCRIPTION)
                            } else {
                                asset
                            }
                        },
                    )
                },
            )
        }
    }

    /**
     * 에셋 개별 구매 완료를 반영한다. 해당 카드에 구매 완료 뱃지가 표시된다.
     *
     * TODO PurchaseRepository 도입 시 repository.purchaseAsset() 호출로 교체.
     */
    fun onAssetPurchased(assetId: Long) {
        _uiState.update { state ->
            val current = state as? ShopUiState.Success ?: return@update state

            current.copy(
                sections = current.sections.map { section ->
                    if (section.assets.none { it.id == assetId }) return@map section

                    section.copy(
                        assets = section.assets.map { asset ->
                            if (asset.id == assetId) {
                                asset.copy(availability = AssetAvailability.PURCHASED)
                            } else {
                                asset
                            }
                        },
                    )
                },
            )
        }
    }

    private fun loadFirstPage() {
        viewModelScope.launch {
            _uiState.update { ShopUiState.Loading }

            val firstPage = ShopMockData.sections.take(PAGE_SIZE)

            _uiState.update {
                ShopUiState.Success(
                    banners = ShopMockData.banners,
                    sections = firstPage,
                    hasNext = firstPage.size < ShopMockData.sections.size,
                )
            }
        }
    }

    private companion object {
        const val PAGE_SIZE = 3
    }
}
