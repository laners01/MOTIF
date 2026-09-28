package com.ssafy.motif.feature.artistdetail.impl.artistdetail

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.motif.core.common.result.Result
import com.ssafy.motif.core.common.result.asResult
import com.ssafy.motif.core.data.repository.ArtistRepository
import com.ssafy.motif.core.model.comment.Comment
import com.ssafy.motif.core.ui.comment.CommentPage
import com.ssafy.motif.core.ui.comment.CommentUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ArtistDetailViewModel @Inject constructor(
    private val artistRepository: ArtistRepository,
) : ViewModel() {

    private val _uiState =
        MutableStateFlow(
            ArtistDetailUiState(),
        )

    internal val uiState: StateFlow<ArtistDetailUiState> =
        _uiState.asStateFlow()

    private var loadedArtistId: Long? = null

    fun loadArtist(
        artistId: Long,
    ) {
        if (loadedArtistId == artistId) {
            return
        }

        loadedArtistId = artistId

        _uiState.value =
            ArtistDetailUiState(
                artistId = artistId,
            )

        loadArtistDetail(
            artistId = artistId,
        )

        loadArtistChallenges(
            artistId = artistId,
        )

        loadArtistNotices(
            artistId = artistId,
        )
    }

    fun selectTab(
        tab: ArtistDetailTab,
    ) {
        _uiState.update { state ->
            state.copy(
                selectedTab = tab,
            )
        }
    }

    fun toggleSubscribe() {
        val currentState =
            _uiState.value

        if (currentState.isSubscriptionUpdating) {
            return
        }

        val detailState =
            currentState.detailState as?
                    ArtistDetailContentUiState.Success
                ?: return

        val previousDetail =
            detailState.artistDetail

        val targetSubscribed =
            !previousDetail.isSubscribed

        val optimisticDetail =
            previousDetail.copy(
                isSubscribed = targetSubscribed,
                subscriberCount =
                    (
                            previousDetail.subscriberCount +
                                    if (targetSubscribed) 1L else -1L
                            ).coerceAtLeast(0L),
            )

        _uiState.update { state ->
            state.copy(
                detailState =
                    ArtistDetailContentUiState.Success(
                        artistDetail = optimisticDetail,
                    ),
                isSubscriptionUpdating = true,
            )
        }

        viewModelScope.launch {
            artistRepository
                .updateArtistSubscription(
                    id = previousDetail.artist.id,
                    isSubscribed = targetSubscribed,
                )
                .asResult()
                .collect { result ->
                    when (result) {
                        Result.Loading -> Unit

                        is Result.Success -> {
                            val subscription =
                                result.data

                            _uiState.update { state ->
                                val currentDetail =
                                    (
                                            state.detailState as?
                                                    ArtistDetailContentUiState.Success
                                            )
                                        ?.artistDetail
                                        ?: optimisticDetail

                                state.copy(
                                    detailState =
                                        ArtistDetailContentUiState.Success(
                                            artistDetail =
                                                currentDetail.copy(
                                                    isSubscribed =
                                                        subscription.isSubscribed,
                                                    subscriberCount =
                                                        subscription.subscriberCount,
                                                ),
                                        ),
                                    isSubscriptionUpdating = false,
                                )
                            }
                        }

                        is Result.Error -> {
                            _uiState.update { state ->
                                state.copy(
                                    detailState =
                                        ArtistDetailContentUiState.Success(
                                            artistDetail = previousDetail,
                                        ),
                                    isSubscriptionUpdating = false,
                                )
                            }
                        }
                    }
                }
        }
    }

    fun toggleAlarm() {
        // TODO 알림 설정 API 추가 시 실제 서버 상태로 변경
        _uiState.update { state ->
            state.copy(
                isAlarmOn = !state.isAlarmOn,
            )
        }
    }

    fun loadMoreNotices() {
        val state =
            _uiState.value

        val noticeState =
            state.noticeState as?
                    ArtistNoticeUiState.Success
                ?: return

        if (!noticeState.hasNext) {
            return
        }

        if (
            noticeState.appendState
                    !is ArtistNoticeAppendUiState.Idle
        ) {
            return
        }

        val nextCursor =
            noticeState.nextCursor
                ?: return

        _uiState.update { current ->
            current.copy(
                noticeState =
                    noticeState.copy(
                        appendState =
                            ArtistNoticeAppendUiState.Loading,
                    ),
            )
        }

        viewModelScope.launch {
            artistRepository
                .getArtistNotices(
                    id = state.artistId,
                    cursor = nextCursor,
                    size = NOTICE_PAGE_SIZE,
                )
                .asResult()
                .collect { result ->
                    when (result) {
                        Result.Loading -> Unit

                        is Result.Success -> {
                            val page =
                                result.data

                            _uiState.update { current ->
                                val latestState =
                                    current.noticeState as?
                                            ArtistNoticeUiState.Success
                                        ?: return@update current

                                current.copy(
                                    noticeState =
                                        latestState.copy(
                                            notices =
                                                latestState.notices +
                                                        page.items,
                                            nextCursor =
                                                page.nextCursor,
                                            hasNext =
                                                page.hasNext,
                                            appendState =
                                                ArtistNoticeAppendUiState.Idle,
                                        ),
                                )
                            }
                        }

                        is Result.Error -> {
                            _uiState.update { current ->
                                val latestState =
                                    current.noticeState as?
                                            ArtistNoticeUiState.Success
                                        ?: return@update current

                                current.copy(
                                    noticeState =
                                        latestState.copy(
                                            appendState =
                                                ArtistNoticeAppendUiState.Error(
                                                    result.exception,
                                                ),
                                        ),
                                )
                            }
                        }
                    }
                }
        }
    }

    fun toggleNoticeLike(
        noticeId: Long,
    ) {
        /*
         * TODO NoticeRepository 좋아요 API 연동.
         *
         * 아티스트 상세 API 연동 범위에서는 기존 UI 동작을 유지하기 위해
         * optimistic local state만 변경한다.
         */
        _uiState.update { state ->
            val noticeState =
                state.noticeState as?
                        ArtistNoticeUiState.Success
                    ?: return@update state

            state.copy(
                noticeState =
                    noticeState.copy(
                        notices =
                            noticeState.notices.map { notice ->
                                if (notice.id != noticeId) {
                                    notice
                                } else {
                                    notice.copy(
                                        isLiked =
                                            !notice.isLiked,
                                        likeCount =
                                            (
                                                    notice.likeCount +
                                                            if (notice.isLiked) {
                                                                -1L
                                                            } else {
                                                                1L
                                                            }
                                                    ).coerceAtLeast(0L),
                                    )
                                }
                            },
                    ),
            )
        }
    }

    fun onNoticeCommentClick(
        noticeId: Long,
    ) {
        val noticeState =
            _uiState.value.noticeState as?
                    ArtistNoticeUiState.Success
                ?: return

        val exists =
            noticeState.notices.any { notice ->
                notice.id == noticeId
            }

        if (!exists) {
            return
        }

        /*
         * TODO CommentRepository 연동.
         *
         * 기존 ArtistDetailMockData는 제거하고
         * 댓글 API가 연결되기 전까지 빈 댓글 상태를 사용한다.
         */
        _uiState.update { state ->
            state.copy(
                commentUiState =
                    CommentUiState(
                        page = CommentPage.Comments,
                        comments = emptyList(),
                        replies = emptyList(),
                        isLoading = false,
                    ),
            )
        }
    }

    fun onReplyClick(
        commentId: Long,
    ) {
        val commentUiState =
            _uiState.value.commentUiState

        val parentComment =
            commentUiState.comments
                .firstOrNull { comment ->
                    comment.id == commentId
                }
                ?: return

        _uiState.update { state ->
            state.copy(
                commentUiState =
                    state.commentUiState.copy(
                        page =
                            CommentPage.Replies(
                                parentComment = parentComment,
                            ),
                        replies = emptyList(),
                        isLoading = false,
                    ),
            )
        }
    }

    fun onCommentBackClick() {
        _uiState.update { state ->
            state.copy(
                commentUiState =
                    state.commentUiState.copy(
                        page = CommentPage.Comments,
                        replies = emptyList(),
                        isLoading = false,
                    ),
            )
        }
    }

    fun onCommentLikeClick(
        commentId: Long,
    ) {
        _uiState.update { state ->
            val commentUiState =
                state.commentUiState

            val comments =
                commentUiState.comments.map { comment ->
                    if (comment.id == commentId) {
                        comment.toggleLike()
                    } else {
                        comment
                    }
                }

            val replies =
                commentUiState.replies.map { reply ->
                    if (reply.id == commentId) {
                        reply.toggleLike()
                    } else {
                        reply
                    }
                }

            val page =
                when (
                    val page =
                        commentUiState.page
                ) {
                    CommentPage.Comments ->
                        page

                    is CommentPage.Replies -> {
                        if (
                            page.parentComment.id ==
                            commentId
                        ) {
                            page.copy(
                                parentComment =
                                    page.parentComment.toggleLike(),
                            )
                        } else {
                            page
                        }
                    }
                }

            state.copy(
                commentUiState =
                    commentUiState.copy(
                        page = page,
                        comments = comments,
                        replies = replies,
                    ),
            )
        }
    }

    fun onCommentSendClick(
        content: String,
    ) {
        if (content.isBlank()) {
            return
        }

        // TODO CommentRepository 작성 API 연동
    }

    private fun loadArtistDetail(
        artistId: Long,
    ) {
        viewModelScope.launch {
            artistRepository
                .getArtistDetail(
                    id = artistId,
                )
                .asResult()
                .collect { result ->
                    when (result) {
                        Result.Loading -> {
                            _uiState.update { state ->
                                state.copy(
                                    detailState =
                                        ArtistDetailContentUiState.Loading,
                                )
                            }
                        }

                        is Result.Success -> {
                            _uiState.update { state ->
                                state.copy(
                                    detailState =
                                        ArtistDetailContentUiState.Success(
                                            artistDetail = result.data,
                                        ),
                                )
                            }
                        }

                        is Result.Error -> {
                            _uiState.update { state ->
                                state.copy(
                                    detailState =
                                        ArtistDetailContentUiState.Error(
                                            exception =
                                                result.exception,
                                        ),
                                )
                            }
                        }
                    }
                }
        }
    }

    private fun loadArtistChallenges(
        artistId: Long,
    ) {
        viewModelScope.launch {
            artistRepository
                .getArtistChallenges(
                    id = artistId,
                )
                .asResult()
                .collect { result ->
                    when (result) {
                        Result.Loading -> {
                            _uiState.update { state ->
                                state.copy(
                                    challengeState =
                                        ArtistChallengeUiState.Loading,
                                )
                            }
                        }

                        is Result.Success -> {
                            _uiState.update { state ->
                                state.copy(
                                    challengeState =
                                        ArtistChallengeUiState.Success(
                                            challenges = result.data,
                                        ),
                                )
                            }
                        }

                        is Result.Error -> {
                            _uiState.update { state ->
                                state.copy(
                                    challengeState =
                                        ArtistChallengeUiState.Error(
                                            exception =
                                                result.exception,
                                        ),
                                )
                            }
                        }
                    }
                }
        }
    }

    private fun loadArtistNotices(
        artistId: Long,
    ) {
        viewModelScope.launch {
            artistRepository
                .getArtistNotices(
                    id = artistId,
                    cursor = null,
                    size = NOTICE_PAGE_SIZE,
                )
                .asResult()
                .collect { result ->
                    when (result) {
                        Result.Loading -> {
                            Log.d(
                                "ArtistDetail",
                                "notice loading artistId=$artistId",
                            )

                            _uiState.update { state ->
                                state.copy(
                                    noticeState = ArtistNoticeUiState.Loading,
                                )
                            }
                        }

                        is Result.Success -> {
                            val page = result.data

                            Log.d(
                                "ArtistDetail",
                                "notice success size=${page.items.size}, hasNext=${page.hasNext}",
                            )

                            _uiState.update { state ->
                                state.copy(
                                    noticeState = ArtistNoticeUiState.Success(
                                        notices = page.items,
                                        nextCursor = page.nextCursor,
                                        hasNext = page.hasNext,
                                    ),
                                )
                            }
                        }

                        is Result.Error -> {
                            Log.e(
                                "ArtistDetail",
                                "notice error",
                                result.exception,
                            )

                            _uiState.update { state ->
                                state.copy(
                                    noticeState = ArtistNoticeUiState.Error(
                                        exception = result.exception,
                                    ),
                                )
                            }
                        }
                    }
                }
        }
    }

    private fun Comment.toggleLike(): Comment =
        copy(
            isLiked = !isLiked,
            likeCount =
                (
                        likeCount +
                                if (isLiked) {
                                    -1L
                                } else {
                                    1L
                                }
                        ).coerceAtLeast(0L),
        )

    private companion object {
        const val NOTICE_PAGE_SIZE = 20
    }
}