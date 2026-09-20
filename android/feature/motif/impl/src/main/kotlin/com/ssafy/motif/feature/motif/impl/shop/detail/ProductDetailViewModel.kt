package com.ssafy.motif.feature.motif.impl.shop.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProductDetailViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow<ProductDetailUiState>(ProductDetailUiState.Loading)
    internal val uiState: StateFlow<ProductDetailUiState> = _uiState.asStateFlow()

    private var loadedArtistId: Long? = null

    /**
     * 아티스트 상세를 불러오고 [assetId]를 초기 선택 에셋으로 둔다.
     * 같은 아티스트가 이미 로드되어 있으면(재구성 등) 다시 불러오지 않는다.
     */
    fun load(artistId: Long, assetId: Long) {
        if (loadedArtistId == artistId) return
        loadedArtistId = artistId

        viewModelScope.launch {
            _uiState.update { ProductDetailUiState.Loading }

            val detail = ProductDetailMockData.detailOf(artistId)

            _uiState.update {
                if (detail == null || detail.assets.isEmpty()) {
                    ProductDetailUiState.Error(
                        IllegalStateException("artist $artistId has no products"),
                    )
                } else {
                    ProductDetailUiState.Success(
                        detail = detail,
                        // 진입 경로의 에셋이 목록에 없으면 첫 에셋으로 대체한다.
                        selectedAsset = detail.assets.firstOrNull { it.id == assetId }
                            ?: detail.assets.first(),
                    )
                }
            }
        }
    }

    /**
     * 에셋 카드를 선택해 상단 미리보기와 선택 상품 카드를 바꾼다.
     */
    fun selectAsset(assetId: Long) {
        _uiState.update { state ->
            val current = state as? ProductDetailUiState.Success ?: return@update state
            val asset = current.detail.assets.firstOrNull { it.id == assetId } ?: return@update state

            current.copy(selectedAsset = asset)
        }
    }

    /**
     * 하단 MOTIF 구매 버튼. 현재 선택된 에셋을 구매 대상으로 넘긴다.
     */
    fun purchaseSelectedAsset() {
        val current = _uiState.value as? ProductDetailUiState.Success ?: return
        if (current.isSelectedAssetPurchased) return

        // TODO 결제 화면 이동 (별도 티켓)
    }
}
