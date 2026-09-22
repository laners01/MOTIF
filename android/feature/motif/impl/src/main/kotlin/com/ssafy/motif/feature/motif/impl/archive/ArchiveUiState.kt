package com.ssafy.motif.feature.motif.impl.archive

import androidx.compose.runtime.Immutable
import com.ssafy.motif.core.model.artist.Artist
import com.ssafy.motif.core.model.motif.SavedMotif

internal sealed interface ArchiveUiState {

    data object Loading : ArchiveUiState

    @Immutable
    data class Success(
        /** 현재 아티스트 필터가 적용된, 로드된 페이지까지의 저장 모티프. */
        val motifs: List<SavedMotif>,
        /** WITH 필터에 노출할 아티스트. 저장 결과물이 있는 아티스트만 포함한다. */
        val artists: List<Artist>,
        /** 선택된 아티스트 id. null이면 전체. */
        val selectedArtistId: Long? = null,
        /**
         * 상단 재생 영역에 표시할 모티프.
         * 필터로 목록에서 빠지거나 아직 로드되지 않은 페이지에 있어도 재생은 이어져야 하므로 id가 아닌 객체로 둔다.
         * 저장된 모티프가 하나도 없으면 null.
         */
        val selectedMotif: SavedMotif?,
        val hasNext: Boolean,
        val appendState: ArchiveAppendUiState = ArchiveAppendUiState.Idle,
        /** 대표 모티프 설정/해제가 진행 중인 항목 id. 진행 중에는 별 아이콘 탭을 막는다. */
        val updatingRepresentativeMotifId: Long? = null,
    ) : ArchiveUiState

    data class Error(
        val exception: Throwable,
    ) : ArchiveUiState
}

internal sealed interface ArchiveAppendUiState {

    data object Idle : ArchiveAppendUiState

    data object Loading : ArchiveAppendUiState

    data class Error(
        val exception: Throwable,
    ) : ArchiveAppendUiState
}
