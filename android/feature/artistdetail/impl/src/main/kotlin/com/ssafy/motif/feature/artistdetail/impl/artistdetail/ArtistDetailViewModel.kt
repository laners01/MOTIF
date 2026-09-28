package com.ssafy.motif.feature.artistdetail.impl.artistdetail

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.motif.core.common.result.Result
import com.ssafy.motif.core.common.result.asResult
import com.ssafy.motif.core.data.repository.ArtistRepository
import com.ssafy.motif.core.data.repository.CommentRepository
import com.ssafy.motif.core.model.comment.Comment
import com.ssafy.motif.core.model.comment.CommentType
import com.ssafy.motif.core.ui.comment.CommentPage
import com.ssafy.motif.core.ui.comment.CommentUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ArtistDetailViewModel @Inject constructor(
    private val artistRepository: ArtistRepository,
    private val commentRepository: CommentRepository,
) : ViewModel() {

    private val _uiState =
        MutableStateFlow(
            ArtistDetailUiState(),
        )

    internal val uiState: StateFlow<ArtistDetailUiState> =
        _uiState.asStateFlow()

    private var loadedArtistId: Long? =
        null

    private var selectedNoticeId: Long? =
        null

    private var commentLoadJob: Job? =
        null

    fun loadArtist(
        artistId: Long,
    ) {
        if (loadedArtistId == artistId) {
            return
        }

        loadedArtistId =
            artistId

        selectedNoticeId =
            null

        commentLoadJob?.cancel()

        _uiState.value =
            ArtistDetailUiState(
                artistId =
                    artistId,
            )

        loadArtistDetail(
            artistId =
                artistId,
        )

        loadArtistChallenges(
            artistId =
                artistId,
        )

        loadArtistNotices(
            artistId =
                artistId,
        )
    }

    fun selectTab(
        tab: ArtistDetailTab,
    ) {
        _uiState.update { state ->
            state.copy(
                selectedTab =
                    tab,
            )
        }
    }

    fun toggleSubscribe() {
        val currentState =
            _uiState.value

        if (
            currentState
                .isSubscriptionUpdating
        ) {
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
                isSubscribed =
                    targetSubscribed,
                subscriberCount =
                    (
                            previousDetail.subscriberCount +
                                    if (targetSubscribed) {
                                        1L
                                    } else {
                                        -1L
                                    }
                            )
                        .coerceAtLeast(
                            0L,
                        ),
            )

        _uiState.update { state ->
            state.copy(
                detailState =
                    ArtistDetailContentUiState.Success(
                        artistDetail =
                            optimisticDetail,
                    ),
                isSubscriptionUpdating =
                    true,
            )
        }

        viewModelScope.launch {
            artistRepository
                .updateArtistSubscription(
                    id =
                        previousDetail.artist.id,
                    isSubscribed =
                        targetSubscribed,
                )
                .asResult()
                .collect { result ->
                    when (result) {
                        Result.Loading ->
                            Unit

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
                                                        subscription
                                                            .isSubscribed,
                                                    subscriberCount =
                                                        subscription
                                                            .subscriberCount,
                                                ),
                                        ),
                                    isSubscriptionUpdating =
                                        false,
                                )
                            }
                        }

                        is Result.Error -> {
                            _uiState.update { state ->
                                state.copy(
                                    detailState =
                                        ArtistDetailContentUiState.Success(
                                            artistDetail =
                                                previousDetail,
                                        ),
                                    isSubscriptionUpdating =
                                        false,
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
                isAlarmOn =
                    !state.isAlarmOn,
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

        if (
            !noticeState.hasNext
        ) {
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
                    id =
                        state.artistId,
                    cursor =
                        nextCursor,
                    size =
                        NOTICE_PAGE_SIZE,
                )
                .asResult()
                .collect { result ->
                    when (result) {
                        Result.Loading ->
                            Unit

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
         * 현재 아티스트 상세 화면에서는
         * 기존 동작을 유지하기 위해 local optimistic update만 수행.
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
                            noticeState.notices
                                .map { notice ->
                                    if (
                                        notice.id !=
                                        noticeId
                                    ) {
                                        notice
                                    } else {
                                        notice.copy(
                                            isLiked =
                                                !notice.isLiked,
                                            likeCount =
                                                (
                                                        notice.likeCount +
                                                                if (
                                                                    notice.isLiked
                                                                ) {
                                                                    -1L
                                                                } else {
                                                                    1L
                                                                }
                                                        )
                                                    .coerceAtLeast(
                                                        0L,
                                                    ),
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
            _uiState.value
                .noticeState as?
                    ArtistNoticeUiState.Success
                ?: return

        val exists =
            noticeState.notices
                .any { notice ->
                    notice.id ==
                            noticeId
                }

        if (!exists) {
            return
        }

        selectedNoticeId =
            noticeId

        commentLoadJob?.cancel()

        commentLoadJob =
            commentRepository
                .getNoticeComments(
                    noticeId =
                        noticeId,
                )
                .asResult()
                .onEach { result ->
                    _uiState.update { state ->
                        state.copy(
                            commentUiState =
                                when (result) {
                                    Result.Loading -> {
                                        CommentUiState(
                                            page =
                                                CommentPage.Comments,
                                            comments =
                                                emptyList(),
                                            replies =
                                                emptyList(),
                                            isLoading =
                                                true,
                                        )
                                    }

                                    is Result.Success -> {
                                        CommentUiState(
                                            page =
                                                CommentPage.Comments,
                                            comments =
                                                result.data.items,
                                            replies =
                                                emptyList(),
                                            isLoading =
                                                false,
                                        )
                                    }

                                    is Result.Error -> {
                                        CommentUiState(
                                            page =
                                                CommentPage.Comments,
                                            comments =
                                                emptyList(),
                                            replies =
                                                emptyList(),
                                            isLoading =
                                                false,
                                        )
                                    }
                                },
                        )
                    }
                }
                .launchIn(
                    viewModelScope,
                )
    }

    fun onReplyClick(
        commentId: Long,
    ) {
        val parentComment =
            _uiState.value
                .commentUiState
                .comments
                .firstOrNull { comment ->
                    comment.id ==
                            commentId
                }
                ?: return

        commentLoadJob?.cancel()

        _uiState.update { state ->
            state.copy(
                commentUiState =
                    state.commentUiState.copy(
                        page =
                            CommentPage.Replies(
                                parentComment =
                                    parentComment,
                            ),
                        replies =
                            emptyList(),
                        isLoading =
                            true,
                    ),
            )
        }

        commentLoadJob =
            commentRepository
                .getReplies(
                    type =
                        CommentType.NOTICE,
                    commentId =
                        commentId,
                )
                .asResult()
                .onEach { result ->
                    when (result) {
                        Result.Loading -> {
                            _uiState.update { state ->
                                state.copy(
                                    commentUiState =
                                        state.commentUiState.copy(
                                            isLoading =
                                                true,
                                        ),
                                )
                            }
                        }

                        is Result.Success -> {
                            _uiState.update { state ->
                                state.copy(
                                    commentUiState =
                                        state.commentUiState.copy(
                                            replies =
                                                result.data.items,
                                            isLoading =
                                                false,
                                        ),
                                )
                            }
                        }

                        is Result.Error -> {
                            _uiState.update { state ->
                                state.copy(
                                    commentUiState =
                                        state.commentUiState.copy(
                                            replies =
                                                emptyList(),
                                            isLoading =
                                                false,
                                        ),
                                )
                            }
                        }
                    }
                }
                .launchIn(
                    viewModelScope,
                )
    }

    fun onCommentBackClick() {
        commentLoadJob?.cancel()

        _uiState.update { state ->
            state.copy(
                commentUiState =
                    state.commentUiState.copy(
                        page =
                            CommentPage.Comments,
                        replies =
                            emptyList(),
                        isLoading =
                            false,
                    ),
            )
        }
    }

    fun dismissComments() {
        commentLoadJob?.cancel()

        selectedNoticeId =
            null

        _uiState.update { state ->
            state.copy(
                commentUiState =
                    CommentUiState(),
            )
        }
    }

    fun onCommentLikeClick(
        commentId: Long,
    ) {
        val comment =
            findComment(
                commentId =
                    commentId,
            ) ?: return

        val previousLiked =
            comment.isLiked

        val previousLikeCount =
            comment.likeCount

        val targetLiked =
            !previousLiked

        val targetLikeCount =
            if (targetLiked) {
                previousLikeCount + 1L
            } else {
                (previousLikeCount - 1L)
                    .coerceAtLeast(
                        0L,
                    )
            }

        updateCommentLikeState(
            commentId =
                commentId,
            isLiked =
                targetLiked,
            likeCount =
                targetLikeCount,
        )

        commentRepository
            .updateCommentLike(
                type =
                    CommentType.NOTICE,
                commentId =
                    commentId,
                isLiked =
                    targetLiked,
            )
            .asResult()
            .onEach { result ->
                if (
                    result is Result.Error
                ) {
                    rollbackCommentLike(
                        commentId =
                            commentId,
                        targetLiked =
                            targetLiked,
                        previousLiked =
                            previousLiked,
                        previousLikeCount =
                            previousLikeCount,
                    )
                }
            }
            .launchIn(
                viewModelScope,
            )
    }

    fun onCommentSendClick(
        content: String,
    ) {
        val noticeId =
            selectedNoticeId
                ?: return

        val trimmedContent =
            content.trim()

        if (
            trimmedContent.isEmpty()
        ) {
            return
        }

        val parentCommentId =
            (
                    _uiState.value
                        .commentUiState
                        .page as?
                            CommentPage.Replies
                    )
                ?.parentComment
                ?.id

        commentRepository
            .createNoticeComment(
                noticeId =
                    noticeId,
                content =
                    trimmedContent,
                parentCommentId =
                    parentCommentId,
            )
            .asResult()
            .onEach { result ->
                if (
                    result !is Result.Success
                ) {
                    return@onEach
                }

                incrementNoticeCommentCount(
                    noticeId =
                        noticeId,
                )

                if (
                    parentCommentId ==
                    null
                ) {
                    onNoticeCommentClick(
                        noticeId =
                            noticeId,
                    )
                } else {
                    incrementReplyCount(
                        commentId =
                            parentCommentId,
                    )

                    onReplyClick(
                        commentId =
                            parentCommentId,
                    )
                }
            }
            .launchIn(
                viewModelScope,
            )
    }

    private fun loadArtistDetail(
        artistId: Long,
    ) {
        viewModelScope.launch {
            artistRepository
                .getArtistDetail(
                    id =
                        artistId,
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
                                            artistDetail =
                                                result.data,
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
                    id =
                        artistId,
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
                                            challenges =
                                                result.data,
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
                    id =
                        artistId,
                    cursor =
                        null,
                    size =
                        NOTICE_PAGE_SIZE,
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
                                    noticeState =
                                        ArtistNoticeUiState.Loading,
                                )
                            }
                        }

                        is Result.Success -> {
                            val page =
                                result.data

                            Log.d(
                                "ArtistDetail",
                                "notice success size=${page.items.size}, hasNext=${page.hasNext}",
                            )

                            _uiState.update { state ->
                                state.copy(
                                    noticeState =
                                        ArtistNoticeUiState.Success(
                                            notices =
                                                page.items,
                                            nextCursor =
                                                page.nextCursor,
                                            hasNext =
                                                page.hasNext,
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
                                    noticeState =
                                        ArtistNoticeUiState.Error(
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

    private fun findComment(
        commentId: Long,
    ): Comment? {
        val state =
            _uiState.value
                .commentUiState

        val parentComment =
            (
                    state.page as?
                            CommentPage.Replies
                    )
                ?.parentComment
                ?.takeIf { comment ->
                    comment.id ==
                            commentId
                }

        return parentComment
            ?: state.comments
                .firstOrNull { comment ->
                    comment.id ==
                            commentId
                }
            ?: state.replies
                .firstOrNull { comment ->
                    comment.id ==
                            commentId
                }
    }

    private fun updateCommentLikeState(
        commentId: Long,
        isLiked: Boolean,
        likeCount: Long,
    ) {
        _uiState.update { state ->
            val commentUiState =
                state.commentUiState

            val comments =
                commentUiState.comments
                    .map { comment ->
                        if (
                            comment.id ==
                            commentId
                        ) {
                            comment.copy(
                                isLiked =
                                    isLiked,
                                likeCount =
                                    likeCount,
                            )
                        } else {
                            comment
                        }
                    }

            val replies =
                commentUiState.replies
                    .map { comment ->
                        if (
                            comment.id ==
                            commentId
                        ) {
                            comment.copy(
                                isLiked =
                                    isLiked,
                                likeCount =
                                    likeCount,
                            )
                        } else {
                            comment
                        }
                    }

            val page =
                when (
                    val currentPage =
                        commentUiState.page
                ) {
                    CommentPage.Comments -> {
                        currentPage
                    }

                    is CommentPage.Replies -> {
                        if (
                            currentPage
                                .parentComment
                                .id ==
                            commentId
                        ) {
                            CommentPage.Replies(
                                parentComment =
                                    currentPage
                                        .parentComment
                                        .copy(
                                            isLiked =
                                                isLiked,
                                            likeCount =
                                                likeCount,
                                        ),
                            )
                        } else {
                            currentPage
                        }
                    }
                }

            state.copy(
                commentUiState =
                    commentUiState.copy(
                        page =
                            page,
                        comments =
                            comments,
                        replies =
                            replies,
                    ),
            )
        }
    }

    private fun rollbackCommentLike(
        commentId: Long,
        targetLiked: Boolean,
        previousLiked: Boolean,
        previousLikeCount: Long,
    ) {
        val currentComment =
            findComment(
                commentId =
                    commentId,
            ) ?: return

        if (
            currentComment.isLiked !=
            targetLiked
        ) {
            return
        }

        updateCommentLikeState(
            commentId =
                commentId,
            isLiked =
                previousLiked,
            likeCount =
                previousLikeCount,
        )
    }

    private fun incrementNoticeCommentCount(
        noticeId: Long,
    ) {
        _uiState.update { state ->
            val noticeState =
                state.noticeState as?
                        ArtistNoticeUiState.Success
                    ?: return@update state

            state.copy(
                noticeState =
                    noticeState.copy(
                        notices =
                            noticeState.notices
                                .map { notice ->
                                    if (
                                        notice.id ==
                                        noticeId
                                    ) {
                                        notice.copy(
                                            commentCount =
                                                notice.commentCount +
                                                        1L,
                                        )
                                    } else {
                                        notice
                                    }
                                },
                    ),
            )
        }
    }

    private fun incrementReplyCount(
        commentId: Long,
    ) {
        _uiState.update { state ->
            val commentUiState =
                state.commentUiState

            val comments =
                commentUiState.comments
                    .map { comment ->
                        if (
                            comment.id ==
                            commentId
                        ) {
                            comment.copy(
                                replyCount =
                                    comment.replyCount +
                                            1L,
                            )
                        } else {
                            comment
                        }
                    }

            val page =
                when (
                    val currentPage =
                        commentUiState.page
                ) {
                    CommentPage.Comments -> {
                        currentPage
                    }

                    is CommentPage.Replies -> {
                        if (
                            currentPage
                                .parentComment
                                .id ==
                            commentId
                        ) {
                            CommentPage.Replies(
                                parentComment =
                                    currentPage
                                        .parentComment
                                        .copy(
                                            replyCount =
                                                currentPage
                                                    .parentComment
                                                    .replyCount +
                                                        1L,
                                        ),
                            )
                        } else {
                            currentPage
                        }
                    }
                }

            state.copy(
                commentUiState =
                    commentUiState.copy(
                        page =
                            page,
                        comments =
                            comments,
                    ),
            )
        }
    }

    private companion object {
        const val NOTICE_PAGE_SIZE =
            20
    }
}