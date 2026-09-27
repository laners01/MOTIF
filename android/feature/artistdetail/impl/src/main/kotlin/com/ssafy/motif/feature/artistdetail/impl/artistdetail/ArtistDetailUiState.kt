package com.ssafy.motif.feature.artistdetail.impl.artistdetail

import com.ssafy.motif.core.model.artist.ArtistDetail
import com.ssafy.motif.core.model.challenge.ChallengeSummary
import com.ssafy.motif.core.model.notice.Notice
import com.ssafy.motif.core.ui.comment.CommentUiState

enum class ArtistDetailTab(
    val label: String,
) {
    Challenge("Challenges"),
    Post("Posts"),
}

internal data class ArtistDetailUiState(
    val artistId: Long = 0L,
    val detailState: ArtistDetailContentUiState =
        ArtistDetailContentUiState.Loading,
    val challengeState: ArtistChallengeUiState =
        ArtistChallengeUiState.Loading,
    val noticeState: ArtistNoticeUiState =
        ArtistNoticeUiState.Loading,
    val selectedTab: ArtistDetailTab =
        ArtistDetailTab.Challenge,
    val isSubscriptionUpdating: Boolean = false,
    val isAlarmOn: Boolean = false,
    val commentUiState: CommentUiState =
        CommentUiState(),
)

internal sealed interface ArtistDetailContentUiState {

    data object Loading : ArtistDetailContentUiState

    data class Success(
        val artistDetail: ArtistDetail,
    ) : ArtistDetailContentUiState

    data class Error(
        val exception: Throwable,
    ) : ArtistDetailContentUiState
}

internal sealed interface ArtistChallengeUiState {

    data object Loading : ArtistChallengeUiState

    data class Success(
        val challenges: List<ChallengeSummary>,
    ) : ArtistChallengeUiState

    data class Error(
        val exception: Throwable,
    ) : ArtistChallengeUiState
}

internal sealed interface ArtistNoticeUiState {

    data object Loading : ArtistNoticeUiState

    data class Success(
        val notices: List<Notice>,
        val nextCursor: String?,
        val hasNext: Boolean,
        val appendState: ArtistNoticeAppendUiState =
            ArtistNoticeAppendUiState.Idle,
    ) : ArtistNoticeUiState

    data class Error(
        val exception: Throwable,
    ) : ArtistNoticeUiState
}

internal sealed interface ArtistNoticeAppendUiState {

    data object Idle : ArtistNoticeAppendUiState

    data object Loading : ArtistNoticeAppendUiState

    data class Error(
        val exception: Throwable,
    ) : ArtistNoticeAppendUiState
}