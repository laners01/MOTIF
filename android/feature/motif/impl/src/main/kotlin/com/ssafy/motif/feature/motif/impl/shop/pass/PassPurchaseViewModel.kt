package com.ssafy.motif.feature.motif.impl.shop.pass

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.motif.feature.motif.impl.shop.detail.ProductDetailMockData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PassPurchaseViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow<PassPurchaseUiState>(PassPurchaseUiState.Loading)
    internal val uiState: StateFlow<PassPurchaseUiState> = _uiState.asStateFlow()

    private var loadedArtistId: Long? = null

    /**
     * 아티스트의 PASS 정보를 불러온다. 같은 아티스트가 이미 로드되어 있으면 다시 불러오지 않는다.
     */
    fun load(artistId: Long) {
        if (loadedArtistId == artistId) return
        loadedArtistId = artistId

        viewModelScope.launch {
            _uiState.update { PassPurchaseUiState.Loading }

            val detail = ProductDetailMockData.detailOf(artistId)

            _uiState.update {
                if (detail == null) {
                    PassPurchaseUiState.Error(
                        IllegalStateException("artist $artistId has no pass"),
                    )
                } else {
                    PassPurchaseUiState.Success(detail = detail)
                }
            }
        }
    }

    /**
     * 플랜 카드의 구매 버튼. 선택한 플랜을 결제 대상으로 넘긴다.
     */
    fun purchasePlan(planId: Long) {
        val current = _uiState.value as? PassPurchaseUiState.Success ?: return
        val plan = current.detail.pass.plans.firstOrNull { it.id == planId } ?: return

        // TODO 결제 진행 (별도 티켓). plan.months, plan.monthlyPrice 전달
    }
}
