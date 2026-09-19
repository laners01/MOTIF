package com.ssafy.motif.feature.motif.impl.shop

import androidx.compose.runtime.Immutable
import com.ssafy.motif.core.model.shop.PassBanner
import com.ssafy.motif.core.model.shop.ShopArtistSection

internal sealed interface ShopUiState {

    data object Loading : ShopUiState

    @Immutable
    data class Success(
        val banners: List<PassBanner>,
        /** 지금까지 로드된 아티스트 섹션. 세로 스크롤 하단 도달 시 추가된다. */
        val sections: List<ShopArtistSection>,
        val hasNext: Boolean,
        val appendState: ShopAppendUiState = ShopAppendUiState.Idle,
    ) : ShopUiState

    data class Error(
        val exception: Throwable,
    ) : ShopUiState
}

internal sealed interface ShopAppendUiState {

    data object Idle : ShopAppendUiState

    data object Loading : ShopAppendUiState

    data class Error(
        val exception: Throwable,
    ) : ShopAppendUiState
}
