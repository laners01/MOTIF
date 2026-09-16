package com.ssafy.motif.feature.artistdetail.impl.artistdetail

import androidx.compose.runtime.Immutable
import com.ssafy.motif.core.model.artistdetail.ArtistDetail
import com.ssafy.motif.core.model.home.Notice
import com.ssafy.motif.core.model.artistdetail.ArtistMotif

enum class ArtistDetailTab(val label: String){
    Challenge("Challenges"),
    Post("Posts"),
}
@Immutable
internal data class ArtistDetailUiState(
    val artistId: Long = 0L,
    val artistDetail: ArtistDetail? = null,
    val notices: List<Notice> = emptyList(),
    val selectedTab: ArtistDetailTab = ArtistDetailTab.Challenge,
    val commentSheet: CommentSheetUiState = CommentSheetUiState.Hidden,
    val challengeState: ChallengeUiState = ChallengeUiState.Loading,
    val motifs: List<ArtistMotif> = emptyList(),
){
    val representativeMotif: ArtistMotif?   //대표 모티프
        get() = motifs.maxByOrNull { it.createdAt }
}
