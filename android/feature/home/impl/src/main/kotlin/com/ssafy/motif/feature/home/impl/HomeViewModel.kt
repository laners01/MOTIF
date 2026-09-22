package com.ssafy.motif.feature.home.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.motif.core.common.result.Result
import com.ssafy.motif.core.common.result.asResult
import com.ssafy.motif.core.data.repository.ArtistRepository
import com.ssafy.motif.core.data.repository.ChallengeRepository
import com.ssafy.motif.core.data.repository.CommentRepository
import com.ssafy.motif.core.data.repository.NoticeRepository
import com.ssafy.motif.core.model.artist.ArtistFeedItem
import com.ssafy.motif.core.model.artist.ArtistFilter
import com.ssafy.motif.core.model.comment.Comment
import com.ssafy.motif.core.model.comment.CommentType
import com.ssafy.motif.core.ui.comment.CommentPage
import com.ssafy.motif.core.ui.comment.CommentUiState
import com.ssafy.motif.feature.home.impl.model.FanRadarUiState
import com.ssafy.motif.feature.home.impl.model.FeedAppendUiState
import com.ssafy.motif.feature.home.impl.model.MyArtistFeedItem
import com.ssafy.motif.feature.home.impl.model.MyArtistFeedUiState
import com.ssafy.motif.feature.home.impl.model.OnstageUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import javax.inject.Inject

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

    private var currentArtistFeedPage = FIRST_PAGE

    private var onstageJob: Job? = null
    private var fanRadarJob: Job? = null
    private var myArtistFeedJob: Job? = null
    private var commentLoadJob: Job? = null

    init {
        loadOnstage()
        loadFanRadar()
        loadMyArtistFeed()
    }

    fun retryOnstage() {
        loadOnstage()
    }

    fun retryFanRadar() {
        loadFanRadar()
    }

    fun retryMyArtistFeed() {
        loadMyArtistFeed()
    }

    fun loadMoreMyArtistFeed() {
        val state =
            _myArtistFeedUiState.value as? MyArtistFeedUiState.Success
                ?: return

        if (
            !state.hasNext ||
            state.appendState !is FeedAppendUiState.Idle
        ) {
            return
        }

        loadNextMyArtistFeedPage()
    }

    fun retryLoadMoreMyArtistFeed() {
        val state =
            _myArtistFeedUiState.value as? MyArtistFeedUiState.Success
                ?: return

        if (
            !state.hasNext ||
            state.appendState !is FeedAppendUiState.Error
        ) {
            return
        }

        loadNextMyArtistFeedPage()
    }

    fun toggleNoticeLike(
        noticeId: Long,
    ) {
        val noticeItem = findNoticeItem(
            noticeId = noticeId,
        ) ?: return

        val previousLiked = noticeItem.notice.isLiked
        val previousLikeCount = noticeItem.notice.likeCount

        val targetLiked = !previousLiked
        val targetLikeCount =
            if (targetLiked) {
                previousLikeCount + 1
            } else {
                (previousLikeCount - 1).coerceAtLeast(0L)
            }

        updateNoticeLikeState(
            noticeId = noticeId,
            isLiked = targetLiked,
            likeCount = targetLikeCount,
        )

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
                        targetLiked = targetLiked,
                        previousLiked = previousLiked,
                        previousLikeCount = previousLikeCount,
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
                                    comments = emptyList(),
                                    replies = emptyList(),
                                    isLoading = true,
                                )
                            }

                            is Result.Success -> {
                                CommentUiState(
                                    page = CommentPage.Comments,
                                    comments = result.data.items,
                                    replies = emptyList(),
                                    isLoading = false,
                                )
                            }

                            is Result.Error -> {
                                CommentUiState(
                                    page = CommentPage.Comments,
                                    comments = emptyList(),
                                    replies = emptyList(),
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
                                    replies = emptyList(),
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

        _commentUiState.value =
            CommentUiState()
    }

    fun toggleCommentLike(
        commentId: Long,
    ) {
        val comment = findComment(
            commentId = commentId,
        ) ?: return

        val previousLiked = comment.isLiked
        val previousLikeCount = comment.likeCount

        val targetLiked = !previousLiked
        val targetLikeCount =
            if (targetLiked) {
                previousLikeCount + 1
            } else {
                (previousLikeCount - 1).coerceAtLeast(0L)
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
                        commentId = commentId,
                        targetLiked = targetLiked,
                        previousLiked = previousLiked,
                        previousLikeCount = previousLikeCount,
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    fun sendComment(
        content: String,
    ) {
        val noticeId =
            selectedNoticeId ?: return

        val trimmedContent =
            content.trim()

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
                if (result !is Result.Success) {
                    return@onEach
                }

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

    private fun loadMyArtistFeed() {
        myArtistFeedJob?.cancel()

        currentArtistFeedPage = FIRST_PAGE

        myArtistFeedJob =
            artistRepository
                .getArtistFeed(
                    page = FIRST_PAGE,
                    size = ARTIST_FEED_SIZE,
                )
                .asResult()
                .onEach { result ->
                    _myArtistFeedUiState.value =
                        when (result) {
                            Result.Loading -> {
                                MyArtistFeedUiState.Loading
                            }

                            is Result.Success -> {
                                currentArtistFeedPage =
                                    result.data.page

                                MyArtistFeedUiState.Success(
                                    items = result.data.items.map { item ->
                                        item.toMyArtistFeedItem()
                                    },
                                    hasNext = result.data.hasNext,
                                )
                            }

                            is Result.Error -> {
                                MyArtistFeedUiState.Error(
                                    exception = result.exception,
                                )
                            }
                        }
                }
                .launchIn(viewModelScope)
    }

    private fun loadNextMyArtistFeedPage() {
        val nextPage =
            currentArtistFeedPage + 1

        _myArtistFeedUiState.update { state ->
            if (state !is MyArtistFeedUiState.Success) {
                return@update state
            }

            state.copy(
                appendState = FeedAppendUiState.Loading,
            )
        }

        myArtistFeedJob?.cancel()

        myArtistFeedJob =
            artistRepository
                .getArtistFeed(
                    page = nextPage,
                    size = ARTIST_FEED_SIZE,
                )
                .asResult()
                .onEach { result ->
                    when (result) {
                        Result.Loading -> Unit

                        is Result.Success -> {
                            currentArtistFeedPage =
                                result.data.page

                            val newItems =
                                result.data.items.map { item ->
                                    item.toMyArtistFeedItem()
                                }

                            _myArtistFeedUiState.update { state ->
                                if (state !is MyArtistFeedUiState.Success) {
                                    return@update state
                                }

                                state.copy(
                                    items = state.items + newItems,
                                    hasNext = result.data.hasNext,
                                    appendState = FeedAppendUiState.Idle,
                                )
                            }
                        }

                        is Result.Error -> {
                            _myArtistFeedUiState.update { state ->
                                if (state !is MyArtistFeedUiState.Success) {
                                    return@update state
                                }

                                state.copy(
                                    appendState = FeedAppendUiState.Error(
                                        exception = result.exception,
                                    ),
                                )
                            }
                        }
                    }
                }
                .launchIn(viewModelScope)
    }

    private fun findNoticeItem(
        noticeId: Long,
    ): MyArtistFeedItem.NoticeItem? {
        val state =
            _myArtistFeedUiState.value as? MyArtistFeedUiState.Success
                ?: return null

        return state.items
            .filterIsInstance<MyArtistFeedItem.NoticeItem>()
            .firstOrNull { item ->
                item.notice.id == noticeId
            }
    }

    private fun updateNoticeLikeState(
        noticeId: Long,
        isLiked: Boolean,
        likeCount: Long,
    ) {
        _myArtistFeedUiState.update { state ->
            if (state !is MyArtistFeedUiState.Success) {
                return@update state
            }

            state.copy(
                items = state.items.map { item ->
                    if (
                        item is MyArtistFeedItem.NoticeItem &&
                        item.notice.id == noticeId
                    ) {
                        item.copy(
                            notice = item.notice.copy(
                                isLiked = isLiked,
                                likeCount = likeCount,
                            ),
                        )
                    } else {
                        item
                    }
                },
            )
        }
    }

    private fun rollbackNoticeLike(
        noticeId: Long,
        targetLiked: Boolean,
        previousLiked: Boolean,
        previousLikeCount: Long,
    ) {
        val currentNotice =
            findNoticeItem(
                noticeId = noticeId,
            )?.notice
                ?: return

        if (currentNotice.isLiked != targetLiked) {
            return
        }

        updateNoticeLikeState(
            noticeId = noticeId,
            isLiked = previousLiked,
            likeCount = previousLikeCount,
        )
    }

    private fun incrementNoticeCommentCount(
        noticeId: Long,
    ) {
        _myArtistFeedUiState.update { state ->
            if (state !is MyArtistFeedUiState.Success) {
                return@update state
            }

            state.copy(
                items = state.items.map { item ->
                    if (
                        item is MyArtistFeedItem.NoticeItem &&
                        item.notice.id == noticeId
                    ) {
                        item.copy(
                            notice = item.notice.copy(
                                commentCount =
                                    item.notice.commentCount + 1,
                            ),
                        )
                    } else {
                        item
                    }
                },
            )
        }
    }

    private fun findComment(
        commentId: Long,
    ): Comment? {
        val state =
            _commentUiState.value

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
                        if (currentPage.parentComment.id == commentId) {
                            CommentPage.Replies(
                                parentComment =
                                    currentPage.parentComment.copy(
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
        commentId: Long,
        targetLiked: Boolean,
        previousLiked: Boolean,
        previousLikeCount: Long,
    ) {
        val currentComment =
            findComment(
                commentId = commentId,
            ) ?: return

        if (currentComment.isLiked != targetLiked) {
            return
        }

        updateCommentLikeState(
            commentId = commentId,
            isLiked = previousLiked,
            likeCount = previousLikeCount,
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
                        if (currentPage.parentComment.id == commentId) {
                            CommentPage.Replies(
                                parentComment =
                                    currentPage.parentComment.copy(
                                        replyCount =
                                            currentPage.parentComment
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
        const val ARTIST_FEED_SIZE = 20
    }
}

private fun ArtistFeedItem.toMyArtistFeedItem(): MyArtistFeedItem =
    when (this) {
        is ArtistFeedItem.MotifAsset -> {
            MyArtistFeedItem.MotifItem(
                motif = motif,
            )
        }

        is ArtistFeedItem.NoticeItem -> {
            MyArtistFeedItem.NoticeItem(
                notice = notice,
            )
        }
    }