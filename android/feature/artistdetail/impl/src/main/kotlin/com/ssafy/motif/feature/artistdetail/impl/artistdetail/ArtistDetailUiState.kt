package com.ssafy.motif.feature.artistdetail.impl.artistdetail

import androidx.compose.runtime.Immutable
import com.ssafy.motif.core.model.artistdetail.ArtistDetail
import com.ssafy.motif.core.model.home.Notice


enum class ArtistDetailTab(val label: String){
    Challenge("Challenges"),
    Post("Posts"),
}
@Immutable
data class ArtistDetailUiState(
    val artistId: Long = 0L,
    val artistDetail: ArtistDetail? = null,
    val notices: List<Notice> = emptyList(),
    val selectedTab: ArtistDetailTab = ArtistDetailTab.Challenge,
    val commentSheet: CommentSheetUiState = CommentSheetUiState.Hidden,
)
