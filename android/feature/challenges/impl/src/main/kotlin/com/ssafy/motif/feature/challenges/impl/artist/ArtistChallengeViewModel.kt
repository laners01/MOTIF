package com.ssafy.motif.feature.challenges.impl.artist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.motif.core.common.result.Result
import com.ssafy.motif.core.common.result.asResult
import com.ssafy.motif.core.data.repository.ArtistRepository
import com.ssafy.motif.core.data.repository.ChallengeRepository
import com.ssafy.motif.core.data.repository.CommentRepository
import com.ssafy.motif.core.model.challenge.Challenge
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
import javax.inject.Inject

@HiltViewModel
internal class ArtistChallengeViewModel @Inject constructor(
    private val challengeRepository: ChallengeRepository,
    private val artistRepository: ArtistRepository,
    private val commentRepository: CommentRepository,
) : ViewModel() {

    private val _uiState =
        MutableStateFlow<ArtistChallengeUiState>(
            ArtistChallengeUiState.Loading,
        )

    val uiState: StateFlow<ArtistChallengeUiState> =
        _uiState.asStateFlow()

    private val _commentUiState =
        MutableStateFlow(
            CommentUiState(),
        )

    val commentUiState: StateFlow<CommentUiState> =
        _commentUiState.asStateFlow()

    private var selectedChallengeId: Long? = null

    private var challengeFeedJob: Job? = null
    private var commentLoadJob: Job? = null

    init {
        loadChallenges()
    }

    fun retryChallenges() {
        loadChallenges()
    }

    fun loadMoreChallenges() {
        val state =
            _uiState.value as? ArtistChallengeUiState.Success
                ?: return

        if (
            !state.hasNext ||
            state.nextCursor == null ||
            state.appendState !is ArtistChallengeAppendUiState.Idle
        ) {
            return
        }

        loadNextChallengePage(
            cursor = state.nextCursor,
        )
    }

    fun retryLoadMoreChallenges() {
        val state =
            _uiState.value as? ArtistChallengeUiState.Success
                ?: return

        if (
            !state.hasNext ||
            state.nextCursor == null ||
            state.appendState !is ArtistChallengeAppendUiState.Error
        ) {
            return
        }

        loadNextChallengePage(
            cursor = state.nextCursor,
        )
    }

    fun toggleChallengeLike(
        challengeId: Long,
    ) {
        val challenge =
            findChallenge(
                challengeId = challengeId,
            ) ?: return

        val previousLiked =
            challenge.isLiked

        val previousLikeCount =
            challenge.likeCount

        val targetLiked =
            !previousLiked

        val targetLikeCount =
            if (targetLiked) {
                previousLikeCount + 1
            } else {
                (previousLikeCount - 1)
                    .coerceAtLeast(0L)
            }

        updateChallengeLikeState(
            challengeId = challengeId,
            isLiked = targetLiked,
            likeCount = targetLikeCount,
        )

        challengeRepository
            .updateChallengeLike(
                id = challengeId,
                isLiked = targetLiked,
            )
            .asResult()
            .onEach { result ->
                if (result is Result.Error) {
                    rollbackChallengeLike(
                        challengeId = challengeId,
                        targetLiked = targetLiked,
                        previousLiked = previousLiked,
                        previousLikeCount = previousLikeCount,
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    fun toggleArtistSubscription(
        artistId: Long,
    ) {
        val challenge =
            findChallengeByArtist(
                artistId = artistId,
            ) ?: return

        val previousSubscribed =
            challenge.isSubscribed

        val targetSubscribed =
            !previousSubscribed

        updateArtistSubscriptionState(
            artistId = artistId,
            isSubscribed = targetSubscribed,
        )

        artistRepository
            .updateArtistSubscription(
                id = artistId,
                isSubscribed = targetSubscribed,
            )
            .asResult()
            .onEach { result ->
                when (result) {
                    Result.Loading -> Unit

                    is Result.Success -> {
                        updateArtistSubscriptionState(
                            artistId = artistId,
                            isSubscribed =
                                result.data.isSubscribed,
                        )
                    }

                    is Result.Error -> {
                        rollbackArtistSubscription(
                            artistId = artistId,
                            targetSubscribed = targetSubscribed,
                            previousSubscribed = previousSubscribed,
                        )
                    }
                }
            }
            .launchIn(viewModelScope)
    }

    fun loadChallengeComments(
        challengeId: Long,
    ) {
        selectedChallengeId =
            challengeId

        commentLoadJob?.cancel()

        commentLoadJob =
            commentRepository
                .getChallengeComments(
                    challengeId = challengeId,
                )
                .asResult()
                .onEach { result ->
                    _commentUiState.value =
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
                        }
                }
                .launchIn(viewModelScope)
    }

    fun loadReplies(
        commentId: Long,
    ) {
        val parentComment =
            _commentUiState.value
                .comments
                .firstOrNull { comment ->
                    comment.id == commentId
                }
                ?: return

        commentLoadJob?.cancel()

        _commentUiState.update { state ->
            state.copy(
                page =
                    CommentPage.Replies(
                        parentComment = parentComment,
                    ),
                replies =
                    emptyList(),
                isLoading =
                    true,
            )
        }

        commentLoadJob =
            commentRepository
                .getReplies(
                    type = CommentType.CHALLENGE,
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
                                    replies =
                                        result.data.items,
                                    isLoading =
                                        false,
                                )
                            }
                        }

                        is Result.Error -> {
                            _commentUiState.update { state ->
                                state.copy(
                                    replies =
                                        emptyList(),
                                    isLoading =
                                        false,
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

        selectedChallengeId = null

        _commentUiState.value =
            CommentUiState()
    }

    fun toggleCommentLike(
        commentId: Long,
    ) {
        val comment =
            findComment(
                commentId = commentId,
            ) ?: return

        val previousLiked =
            comment.isLiked

        val previousLikeCount =
            comment.likeCount

        val targetLiked =
            !previousLiked

        val targetLikeCount =
            if (targetLiked) {
                previousLikeCount + 1
            } else {
                (previousLikeCount - 1)
                    .coerceAtLeast(0L)
            }

        updateCommentLikeState(
            commentId = commentId,
            isLiked = targetLiked,
            likeCount = targetLikeCount,
        )

        commentRepository
            .updateCommentLike(
                type = CommentType.CHALLENGE,
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
        val challengeId =
            selectedChallengeId
                ?: return

        val trimmedContent =
            content.trim()

        if (trimmedContent.isEmpty()) {
            return
        }

        val parentCommentId =
            (
                    _commentUiState.value.page
                            as? CommentPage.Replies
                    )
                ?.parentComment
                ?.id

        commentRepository
            .createChallengeComment(
                challengeId = challengeId,
                content = trimmedContent,
                parentCommentId = parentCommentId,
            )
            .asResult()
            .onEach { result ->
                if (result !is Result.Success) {
                    return@onEach
                }

                incrementChallengeCommentCount(
                    challengeId = challengeId,
                )

                if (parentCommentId == null) {
                    loadChallengeComments(
                        challengeId = challengeId,
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

    private fun loadChallenges() {
        challengeFeedJob?.cancel()

        challengeFeedJob =
            challengeRepository
                .getChallengeFeed(
                    cursor = null,
                    size = CHALLENGE_FEED_SIZE,
                )
                .asResult()
                .onEach { result ->
                    _uiState.value =
                        when (result) {
                            Result.Loading -> {
                                ArtistChallengeUiState.Loading
                            }

                            is Result.Success -> {
                                ArtistChallengeUiState.Success(
                                    challenges =
                                        result.data.items,
                                    nextCursor =
                                        result.data.nextCursor,
                                    hasNext =
                                        result.data.hasNext,
                                )
                            }

                            is Result.Error -> {
                                ArtistChallengeUiState.Error(
                                    exception =
                                        result.exception,
                                )
                            }
                        }
                }
                .launchIn(viewModelScope)
    }

    private fun loadNextChallengePage(
        cursor: String,
    ) {
        _uiState.update { state ->
            if (state !is ArtistChallengeUiState.Success) {
                return@update state
            }

            state.copy(
                appendState =
                    ArtistChallengeAppendUiState.Loading,
            )
        }

        challengeFeedJob?.cancel()

        challengeFeedJob =
            challengeRepository
                .getChallengeFeed(
                    cursor = cursor,
                    size = CHALLENGE_FEED_SIZE,
                )
                .asResult()
                .onEach { result ->
                    when (result) {
                        Result.Loading -> Unit

                        is Result.Success -> {
                            _uiState.update { state ->
                                if (
                                    state !is
                                            ArtistChallengeUiState.Success
                                ) {
                                    return@update state
                                }

                                state.copy(
                                    challenges =
                                        (
                                                state.challenges +
                                                        result.data.items
                                                )
                                            .distinctBy {
                                                it.id
                                            },
                                    nextCursor =
                                        result.data.nextCursor,
                                    hasNext =
                                        result.data.hasNext,
                                    appendState =
                                        ArtistChallengeAppendUiState.Idle,
                                )
                            }
                        }

                        is Result.Error -> {
                            _uiState.update { state ->
                                if (
                                    state !is
                                            ArtistChallengeUiState.Success
                                ) {
                                    return@update state
                                }

                                state.copy(
                                    appendState =
                                        ArtistChallengeAppendUiState.Error(
                                            exception =
                                                result.exception,
                                        ),
                                )
                            }
                        }
                    }
                }
                .launchIn(viewModelScope)
    }

    private fun findChallenge(
        challengeId: Long,
    ): Challenge? {
        val state =
            _uiState.value
                    as? ArtistChallengeUiState.Success
                ?: return null

        return state.challenges
            .firstOrNull { challenge ->
                challenge.id == challengeId
            }
    }

    private fun findChallengeByArtist(
        artistId: Long,
    ): Challenge? {
        val state =
            _uiState.value
                    as? ArtistChallengeUiState.Success
                ?: return null

        return state.challenges
            .firstOrNull { challenge ->
                challenge.artist.id == artistId
            }
    }

    private fun updateChallengeLikeState(
        challengeId: Long,
        isLiked: Boolean,
        likeCount: Long,
    ) {
        _uiState.update { state ->
            if (state !is ArtistChallengeUiState.Success) {
                return@update state
            }

            state.copy(
                challenges =
                    state.challenges.map { challenge ->
                        if (challenge.id == challengeId) {
                            challenge.copy(
                                isLiked = isLiked,
                                likeCount = likeCount,
                            )
                        } else {
                            challenge
                        }
                    },
            )
        }
    }

    private fun rollbackChallengeLike(
        challengeId: Long,
        targetLiked: Boolean,
        previousLiked: Boolean,
        previousLikeCount: Long,
    ) {
        val currentChallenge =
            findChallenge(
                challengeId = challengeId,
            ) ?: return

        if (
            currentChallenge.isLiked !=
            targetLiked
        ) {
            return
        }

        updateChallengeLikeState(
            challengeId = challengeId,
            isLiked = previousLiked,
            likeCount = previousLikeCount,
        )
    }

    private fun updateArtistSubscriptionState(
        artistId: Long,
        isSubscribed: Boolean,
    ) {
        _uiState.update { state ->
            if (state !is ArtistChallengeUiState.Success) {
                return@update state
            }

            state.copy(
                challenges =
                    state.challenges.map { challenge ->
                        if (
                            challenge.artist.id ==
                            artistId
                        ) {
                            challenge.copy(
                                isSubscribed =
                                    isSubscribed,
                            )
                        } else {
                            challenge
                        }
                    },
            )
        }
    }

    private fun rollbackArtistSubscription(
        artistId: Long,
        targetSubscribed: Boolean,
        previousSubscribed: Boolean,
    ) {
        val currentChallenge =
            findChallengeByArtist(
                artistId = artistId,
            ) ?: return

        if (
            currentChallenge.isSubscribed !=
            targetSubscribed
        ) {
            return
        }

        updateArtistSubscriptionState(
            artistId = artistId,
            isSubscribed = previousSubscribed,
        )
    }

    private fun incrementChallengeCommentCount(
        challengeId: Long,
    ) {
        _uiState.update { state ->
            if (state !is ArtistChallengeUiState.Success) {
                return@update state
            }

            state.copy(
                challenges =
                    state.challenges.map { challenge ->
                        if (
                            challenge.id ==
                            challengeId
                        ) {
                            challenge.copy(
                                commentCount =
                                    challenge.commentCount + 1,
                            )
                        } else {
                            challenge
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
            (
                    state.page
                            as? CommentPage.Replies
                    )
                ?.parentComment
                ?.takeIf { comment ->
                    comment.id == commentId
                }

        return parentComment
            ?: state.comments
                .firstOrNull { comment ->
                    comment.id == commentId
                }
            ?: state.replies
                .firstOrNull { comment ->
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
                when (
                    val currentPage =
                        state.page
                ) {
                    CommentPage.Comments -> {
                        currentPage
                    }

                    is CommentPage.Replies -> {
                        if (
                            currentPage
                                .parentComment
                                .id == commentId
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

        if (
            currentComment.isLiked !=
            targetLiked
        ) {
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
                    if (
                        comment.id ==
                        commentId
                    ) {
                        comment.copy(
                            replyCount =
                                comment.replyCount + 1,
                        )
                    } else {
                        comment
                    }
                }

            val page =
                when (
                    val currentPage =
                        state.page
                ) {
                    CommentPage.Comments -> {
                        currentPage
                    }

                    is CommentPage.Replies -> {
                        if (
                            currentPage
                                .parentComment
                                .id == commentId
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
        const val CHALLENGE_FEED_SIZE = 20
    }
}