package com.ssafy.motif.feature.home.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.motif.core.common.result.Result
import com.ssafy.motif.core.common.result.asResult
import com.ssafy.motif.core.data.repository.ArtistRepository
import com.ssafy.motif.core.data.repository.ChallengeRepository
import com.ssafy.motif.core.data.repository.CommentRepository
import com.ssafy.motif.core.data.repository.NoticeRepository
import com.ssafy.motif.core.model.artist.ArtistFilter
import com.ssafy.motif.core.model.comment.Comment
import com.ssafy.motif.core.model.comment.CommentType
import com.ssafy.motif.core.model.notice.Notice
import com.ssafy.motif.core.ui.comment.CommentPage
import com.ssafy.motif.core.ui.comment.CommentUiState
import com.ssafy.motif.feature.home.impl.model.FanRadarUiState
import com.ssafy.motif.feature.home.impl.model.MyArtistFeedItem
import com.ssafy.motif.feature.home.impl.model.MyArtistFeedUiState
import com.ssafy.motif.feature.home.impl.model.OnstageUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update

@HiltViewModel
internal class HomeViewModel @Inject constructor(
    private val challengeRepository: ChallengeRepository,
    private val artistRepository: ArtistRepository,
    private val noticeRepository: NoticeRepository,
    private val commentRepository: CommentRepository,
) : ViewModel() {

    private val _onstageUiState =
        MutableStateFlow<OnstageUiState>(
            OnstageUiState.Loading,
        )
    val onstageUiState: StateFlow<OnstageUiState> =
        _onstageUiState.asStateFlow()

    private val _fanRadarUiState =
        MutableStateFlow<FanRadarUiState>(
            FanRadarUiState.Loading,
        )
    val fanRadarUiState: StateFlow<FanRadarUiState> =
        _fanRadarUiState.asStateFlow()

    /*
     * TODO:
     * HOME 구독 아티스트 통합 피드 Repository 구현 후 연결
     */
    private val _myArtistFeedUiState =
        MutableStateFlow<MyArtistFeedUiState>(
            MyArtistFeedUiState.Loading,
        )
    val myArtistFeedUiState: StateFlow<MyArtistFeedUiState> =
        _myArtistFeedUiState.asStateFlow()

    private val _commentUiState =
        MutableStateFlow(
            CommentUiState(),
        )
    val commentUiState: StateFlow<CommentUiState> =
        _commentUiState.asStateFlow()

    private var selectedNoticeId: Long? = null

    private var onstageJob: Job? = null
    private var fanRadarJob: Job? = null
    private var commentLoadJob: Job? = null

    init {
        loadOnstage()
        loadFanRadar()
    }

    fun retryOnstage() {
        loadOnstage()
    }

    fun retryFanRadar() {
        loadFanRadar()
    }

    fun toggleNoticeLike(
        noticeId: Long,
    ) {
        val notice = findNotice(
            noticeId = noticeId,
        ) ?: return

        val targetLiked = !notice.isLiked
        val targetLikeCount =
            if (targetLiked) {
                notice.likeCount + 1
            } else {
                (notice.likeCount - 1).coerceAtLeast(0L)
            }

        updateNotice(
            noticeId = noticeId,
        ) { currentNotice ->
            currentNotice.copy(
                isLiked = targetLiked,
                likeCount = targetLikeCount,
            )
        }

        noticeRepository
            .updateNoticeLike(
                id = noticeId,
                isLiked = targetLiked,
            )
            .asResult()
            .onEach { result ->
                if (result is Result.Error) {
                    rollbackNoticeLike(
                        noticeId = noticeId,
                        previousNotice = notice,
                        targetLiked = targetLiked,
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    fun loadNoticeComments(
        noticeId: Long,
    ) {
        selectedNoticeId = noticeId

        commentLoadJob?.cancel()

        commentLoadJob =
            commentRepository
                .getNoticeComments(
                    noticeId = noticeId,
                )
                .asResult()
                .onEach { result ->
                    _commentUiState.value =
                        when (result) {
                            Result.Loading -> {
                                CommentUiState(
                                    page = CommentPage.Comments,
                                    isLoading = true,
                                )
                            }

                            is Result.Success -> {
                                CommentUiState(
                                    page = CommentPage.Comments,
                                    comments = result.data.items,
                                    isLoading = false,
                                )
                            }

                            is Result.Error -> {
                                CommentUiState(
                                    page = CommentPage.Comments,
                                    isLoading = false,
                                )
                            }
                        }
                }
                .launchIn(viewModelScope)
    }

    fun loadReplies(
        commentId: Long,
    ) {
        val parentComment =
            _commentUiState.value.comments
                .firstOrNull { comment ->
                    comment.id == commentId
                }
                ?: return

        commentLoadJob?.cancel()

        _commentUiState.update { state ->
            state.copy(
                page = CommentPage.Replies(
                    parentComment = parentComment,
                ),
                replies = emptyList(),
                isLoading = true,
            )
        }

        commentLoadJob =
            commentRepository
                .getReplies(
                    type = CommentType.NOTICE,
                    commentId = commentId,
                )
                .asResult()
                .onEach { result ->
                    when (result) {
                        Result.Loading -> {
                            _commentUiState.update { state ->
                                state.copy(
                                    isLoading = true,
                                )
                            }
                        }

                        is Result.Success -> {
                            _commentUiState.update { state ->
                                state.copy(
                                    replies = result.data.items,
                                    isLoading = false,
                                )
                            }
                        }

                        is Result.Error -> {
                            _commentUiState.update { state ->
                                state.copy(
                                    isLoading = false,
                                )
                            }
                        }
                    }
                }
                .launchIn(viewModelScope)
    }

    fun backToComments() {
        commentLoadJob?.cancel()

        _commentUiState.update { state ->
            state.copy(
                page = CommentPage.Comments,
                replies = emptyList(),
                isLoading = false,
            )
        }
    }

    fun dismissComments() {
        commentLoadJob?.cancel()

        selectedNoticeId = null
        _commentUiState.value = CommentUiState()
    }

    fun toggleCommentLike(
        commentId: Long,
    ) {
        val comment = findComment(
            commentId = commentId,
        ) ?: return

        val targetLiked = !comment.isLiked
        val targetLikeCount =
            if (targetLiked) {
                comment.likeCount + 1
            } else {
                (comment.likeCount - 1).coerceAtLeast(0L)
            }

        updateCommentLikeState(
            commentId = commentId,
            isLiked = targetLiked,
            likeCount = targetLikeCount,
        )

        commentRepository
            .updateCommentLike(
                type = CommentType.NOTICE,
                commentId = commentId,
                isLiked = targetLiked,
            )
            .asResult()
            .onEach { result ->
                if (result is Result.Error) {
                    rollbackCommentLike(
                        previousComment = comment,
                        targetLiked = targetLiked,
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    fun sendComment(
        content: String,
    ) {
        val noticeId = selectedNoticeId
            ?: return

        val trimmedContent = content.trim()

        if (trimmedContent.isEmpty()) {
            return
        }

        val parentCommentId =
            (_commentUiState.value.page as? CommentPage.Replies)
                ?.parentComment
                ?.id

        commentRepository
            .createNoticeComment(
                noticeId = noticeId,
                content = trimmedContent,
                parentCommentId = parentCommentId,
            )
            .asResult()
            .onEach { result ->
                if (result is Result.Success) {
                    incrementNoticeCommentCount(
                        noticeId = noticeId,
                    )

                    if (parentCommentId == null) {
                        loadNoticeComments(
                            noticeId = noticeId,
                        )
                    } else {
                        incrementReplyCount(
                            commentId = parentCommentId,
                        )

                        loadReplies(
                            commentId = parentCommentId,
                        )
                    }
                }
            }
            .launchIn(viewModelScope)
    }

    private fun loadOnstage() {
        onstageJob?.cancel()

        onstageJob =
            challengeRepository
                .getHotChallengeParticipations(
                    cursor = null,
                    size = ONSTAGE_SIZE,
                )
                .asResult()
                .onEach { result ->
                    _onstageUiState.value =
                        when (result) {
                            Result.Loading -> {
                                OnstageUiState.Loading
                            }

                            is Result.Success -> {
                                OnstageUiState.Success(
                                    items = result.data.items,
                                )
                            }

                            is Result.Error -> {
                                OnstageUiState.Error(
                                    exception = result.exception,
                                )
                            }
                        }
                }
                .launchIn(viewModelScope)
    }

    private fun loadFanRadar() {
        fanRadarJob?.cancel()

        fanRadarJob =
            artistRepository
                .getArtistRanking(
                    filter = ArtistFilter.ALL,
                    page = FIRST_PAGE,
                    size = FAN_RADAR_SIZE,
                )
                .asResult()
                .onEach { result ->
                    _fanRadarUiState.value =
                        when (result) {
                            Result.Loading -> {
                                FanRadarUiState.Loading
                            }

                            is Result.Success -> {
                                FanRadarUiState.Success(
                                    artists = result.data.items,
                                )
                            }

                            is Result.Error -> {
                                FanRadarUiState.Error(
                                    exception = result.exception,
                                )
                            }
                        }
                }
                .launchIn(viewModelScope)
    }

    private fun findNotice(
        noticeId: Long,
    ): Notice? {
        val state =
            _myArtistFeedUiState.value as?
                    MyArtistFeedUiState.Success
                ?: return null

        return state.items
            .filterIsInstance<MyArtistFeedItem.NoticeItem>()
            .firstOrNull { item ->
                item.notice.id == noticeId
            }
            ?.notice
    }

    private fun updateNotice(
        noticeId: Long,
        transform: (Notice) -> Notice,
    ) {
        _myArtistFeedUiState.update { state ->
            if (state !is MyArtistFeedUiState.Success) {
                return@update state
            }

            state.copy(
                items = state.items.map { item ->
                    when (item) {
                        is MyArtistFeedItem.NoticeItem -> {
                            if (item.notice.id == noticeId) {
                                item.copy(
                                    notice = transform(
                                        item.notice,
                                    ),
                                )
                            } else {
                                item
                            }
                        }

                        is MyArtistFeedItem.MotifItem -> {
                            item
                        }
                    }
                },
            )
        }
    }

    private fun rollbackNoticeLike(
        noticeId: Long,
        previousNotice: Notice,
        targetLiked: Boolean,
    ) {
        val currentNotice =
            findNotice(
                noticeId = noticeId,
            ) ?: return

        if (currentNotice.isLiked != targetLiked) {
            return
        }

        updateNotice(
            noticeId = noticeId,
        ) {
            previousNotice
        }
    }

    private fun incrementNoticeCommentCount(
        noticeId: Long,
    ) {
        updateNotice(
            noticeId = noticeId,
        ) { notice ->
            notice.copy(
                commentCount = notice.commentCount + 1,
            )
        }
    }

    private fun findComment(
        commentId: Long,
    ): Comment? {
        val state = _commentUiState.value

        val parentComment =
            (state.page as? CommentPage.Replies)
                ?.parentComment
                ?.takeIf { comment ->
                    comment.id == commentId
                }

        return parentComment
            ?: state.comments.firstOrNull { comment ->
                comment.id == commentId
            }
            ?: state.replies.firstOrNull { comment ->
                comment.id == commentId
            }
    }

    private fun updateCommentLikeState(
        commentId: Long,
        isLiked: Boolean,
        likeCount: Long,
    ) {
        _commentUiState.update { state ->
            val comments =
                state.comments.map { comment ->
                    if (comment.id == commentId) {
                        comment.copy(
                            isLiked = isLiked,
                            likeCount = likeCount,
                        )
                    } else {
                        comment
                    }
                }

            val replies =
                state.replies.map { comment ->
                    if (comment.id == commentId) {
                        comment.copy(
                            isLiked = isLiked,
                            likeCount = likeCount,
                        )
                    } else {
                        comment
                    }
                }

            val page =
                when (val currentPage = state.page) {
                    CommentPage.Comments -> {
                        currentPage
                    }

                    is CommentPage.Replies -> {
                        if (
                            currentPage.parentComment.id ==
                            commentId
                        ) {
                            CommentPage.Replies(
                                parentComment =
                                    currentPage
                                        .parentComment
                                        .copy(
                                            isLiked = isLiked,
                                            likeCount = likeCount,
                                        ),
                            )
                        } else {
                            currentPage
                        }
                    }
                }

            state.copy(
                page = page,
                comments = comments,
                replies = replies,
            )
        }
    }

    private fun rollbackCommentLike(
        previousComment: Comment,
        targetLiked: Boolean,
    ) {
        val currentComment =
            findComment(
                commentId = previousComment.id,
            ) ?: return

        if (currentComment.isLiked != targetLiked) {
            return
        }

        updateCommentLikeState(
            commentId = previousComment.id,
            isLiked = previousComment.isLiked,
            likeCount = previousComment.likeCount,
        )
    }

    private fun incrementReplyCount(
        commentId: Long,
    ) {
        _commentUiState.update { state ->
            val comments =
                state.comments.map { comment ->
                    if (comment.id == commentId) {
                        comment.copy(
                            replyCount =
                                comment.replyCount + 1,
                        )
                    } else {
                        comment
                    }
                }

            val page =
                when (val currentPage = state.page) {
                    CommentPage.Comments -> {
                        currentPage
                    }

                    is CommentPage.Replies -> {
                        if (
                            currentPage.parentComment.id ==
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
                                                    .replyCount + 1,
                                        ),
                            )
                        } else {
                            currentPage
                        }
                    }
                }

            state.copy(
                page = page,
                comments = comments,
            )
        }
    }

    private companion object {
        const val FIRST_PAGE = 0
        const val ONSTAGE_SIZE = 10
        const val FAN_RADAR_SIZE = 4
    }
}