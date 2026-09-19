package com.ssafy.motif.feature.motif.impl.assets

import androidx.compose.runtime.Immutable
import com.ssafy.motif.core.model.home.Artist
import com.ssafy.motif.core.model.motif.Asset

internal sealed interface AssetListUiState {

    data object Loading : AssetListUiState

    @Immutable
    data class Success(
        /** 현재 아티스트 필터가 적용된, 사용 가능한 에셋 목록. */
        val assets: List<Asset>,
        /** 필터에 노출할 아티스트. 사용 가능한 에셋이 있는 아티스트만 포함한다. */
        val artists: List<Artist>,
        /** 선택된 아티스트 id. null이면 ALL. */
        val selectedArtistId: Long? = null,
        val hasNext: Boolean,
        val appendState: AssetListAppendUiState = AssetListAppendUiState.Idle,
    ) : AssetListUiState

    data class Error(
        val exception: Throwable,
    ) : AssetListUiState
}

internal sealed interface AssetListAppendUiState {

    data object Idle : AssetListAppendUiState

    data object Loading : AssetListAppendUiState

    data class Error(
        val exception: Throwable,
    ) : AssetListAppendUiState
}
