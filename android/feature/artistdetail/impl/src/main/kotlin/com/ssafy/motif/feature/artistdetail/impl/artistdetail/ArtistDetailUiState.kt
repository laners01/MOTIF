package com.ssafy.motif.feature.artistdetail.impl.artistdetail

import androidx.compose.runtime.Immutable
import com.ssafy.motif.core.model.artist.ArtistDetail
import com.ssafy.motif.core.model.motif.MotifSummary
import com.ssafy.motif.core.model.home.Notice
import com.ssafy.motif.core.ui.comment.CommentUiState

enum class ArtistDetailTab(
    val label: String,
) {
    Challenge("Challenges"),
    Post("Posts"),
}

@Immutable
internal data class ArtistDetailUiState(
    val artistId: Long = 0L,
    val artistDetail: ArtistDetail? = null,
    val notices: List<Notice> = emptyList(),
    val selectedTab: ArtistDetailTab = ArtistDetailTab.Challenge,
    val commentUiState: CommentUiState = CommentUiState(),
    val challengeState: ChallengeUiState = ChallengeUiState.Loading,
    val motifs: List<MotifSummary> = emptyList(),
) {
    val representativeMotif: MotifSummary?
        get() = motifs.maxByOrNull { it.createdAt }
}