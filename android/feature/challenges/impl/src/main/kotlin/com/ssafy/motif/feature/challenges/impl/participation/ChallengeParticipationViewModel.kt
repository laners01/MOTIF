package com.ssafy.motif.feature.challenges.impl.participation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.motif.core.common.result.Result
import com.ssafy.motif.core.common.result.asResult
import com.ssafy.motif.core.data.repository.ChallengeRepository
import com.ssafy.motif.core.data.repository.CommentRepository
import com.ssafy.motif.core.model.challenge.ChallengeParticipation
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
internal class ChallengeParticipationViewModel @Inject constructor(
    private val challengeRepository: ChallengeRepository,
    private val commentRepository: CommentRepository,
) : ViewModel() {

    private val _uiState =
        MutableStateFlow<ChallengeParticipationUiState>(
            ChallengeParticipationUiState.Loading,
        )

    val uiState: StateFlow<ChallengeParticipationUiState> =
        _uiState.asStateFlow()

    private val _commentUiState =
        MutableStateFlow(
            CommentUiState(),
        )

    val commentUiState: StateFlow<CommentUiState> =
        _commentUiState.asStateFlow()

    private var initializedEntry: Entry? = null

    private var selectedVideoId: Long? = null

    private var entryLoadJob: Job? = null
    private var feedLoadJob: Job? = null
    private var commentLoadJob: Job? = null
    private var deleteJob: Job? = null

    fun initialize(
        challengeId: Long?,
        initialVideoId: Long?,
    ) {
        require(
            challengeId != null ||
                    initialVideoId != null,
        )

        val entry =
            Entry(
                challengeId = challengeId,
                initialVideoId = initialVideoId,
            )

        if (initializedEntry == entry) {
            return
        }

        initializedEntry = entry

        loadEntry(
            entry = entry,
        )
    }

    fun retry() {
        val entry =
            initializedEntry
                ?: return

        loadEntry(
            entry = entry,
        )
    }

    fun loadMoreParticipations() {
        val state =
            _uiState.value
                    as? ChallengeParticipationUiState.Success
                ?: return

        if (
            !state.hasNext ||
            state.nextCursor == null ||
            state.appendState
                    !is ChallengeParticipationAppendUiState.Idle
        ) {
            return
        }

        loadNextParticipationPage(
            challengeId = state.challengeId,
            cursor = state.nextCursor,
        )
    }

    fun retryLoadMoreParticipations() {
        val state =
            _uiState.value
                    as? ChallengeParticipationUiState.Success
                ?: return

        if (
            !state.hasNext ||
            state.nextCursor == null ||
            state.appendState
                    !is ChallengeParticipationAppendUiState.Error
        ) {
            return
        }

        loadNextParticipationPage(
            challengeId = state.challengeId,
            cursor = state.nextCursor,
        )
    }

    fun toggleParticipationLike(
        videoId: Long,
    ) {
        val participation =
            findParticipation(
                videoId = videoId,
            ) ?: return

        val previousLiked =
            participation.isLiked

        val previousLikeCount =
            participation.likeCount

        val targetLiked =
            !previousLiked

        val targetLikeCount =
            if (targetLiked) {
                previousLikeCount + 1
            } else {
                (previousLikeCount - 1)
                    .coerceAtLeast(0L)
            }

        updateParticipationLikeState(
            videoId = videoId,
            isLiked = targetLiked,
            likeCount = targetLikeCount,
        )

        challengeRepository
            .updateChallengeParticipationLike(
                id = videoId,
                isLiked = targetLiked,
            )
            .asResult()
            .onEach { result ->
                if (result is Result.Error) {
                    rollbackParticipationLike(
                        videoId = videoId,
                        targetLiked = targetLiked,
                        previousLiked = previousLiked,
                        previousLikeCount =
                            previousLikeCount,
                    )
                }
            }
            .launchIn(
                viewModelScope,
            )
    }

    fun deleteParticipation(
        videoId: Long,
    ) {
        val participation =
            findParticipation(
                videoId = videoId,
            ) ?: return

        if (!participation.isMine) {
            return
        }

        deleteJob?.cancel()

        deleteJob =
            challengeRepository
                .deleteChallengeParticipation(
                    id = videoId,
                )
                .asResult()
                .onEach { result ->
                    if (result !is Result.Success) {
                        return@onEach
                    }

                    if (selectedVideoId == videoId) {
                        selectedVideoId = null
                        _commentUiState.value =
                            CommentUiState()
                    }

                    _uiState.update { state ->
                        if (
                            state !is
                                    ChallengeParticipationUiState.Success
                        ) {
                            return@update state
                        }

                        val updatedParticipations =
                            state.participations
                                .filterNot { item ->
                                    item.id == videoId
                                }

                        if (
                            updatedParticipations
                                .isEmpty()
                        ) {
                            ChallengeParticipationUiState.Empty(
                                challengeId =
                                    state.challengeId,
                            )
                        } else {
                            state.copy(
                                participations =
                                    updatedParticipations,
                                initialPage =
                                    state.initialPage
                                        .coerceAtMost(
                                            updatedParticipations
                                                .lastIndex,
                                        ),
                            )
                        }
                    }
                }
                .launchIn(
                    viewModelScope,
                )
    }

    fun loadVideoComments(
        videoId: Long,
    ) {
        val participation =
            findParticipation(
                videoId = videoId,
            ) ?: return

        if (!participation.commentEnabled) {
            return
        }

        selectedVideoId =
            videoId

        commentLoadJob?.cancel()

        commentLoadJob =
            commentRepository
                .getVideoComments(
                    videoId = videoId,
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
                .launchIn(
                    viewModelScope,
                )
    }

    fun loadReplies(
        commentId: Long,
    ) {
        val parentComment =
            _commentUiState.value
                .comments
                .firstOrNull { comment ->
                    comment.id ==
                            commentId
                }
                ?: return

        commentLoadJob?.cancel()

        _commentUiState.update { state ->
            state.copy(
                page =
                    CommentPage.Replies(
                        parentComment =
                            parentComment,
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
                    type =
                        CommentType.VIDEO,
                    commentId =
                        commentId,
                )
                .asResult()
                .onEach { result ->
                    when (result) {
                        Result.Loading -> {
                            _commentUiState.update { state ->
                                state.copy(
                                    isLoading =
                                        true,
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
                .launchIn(
                    viewModelScope,
                )
    }

    fun backToComments() {
        commentLoadJob?.cancel()

        _commentUiState.update { state ->
            state.copy(
                page =
                    CommentPage.Comments,
                replies =
                    emptyList(),
                isLoading =
                    false,
            )
        }
    }

    fun dismissComments() {
        commentLoadJob?.cancel()

        selectedVideoId =
            null

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
                type =
                    CommentType.VIDEO,
                commentId =
                    commentId,
                isLiked =
                    targetLiked,
            )
            .asResult()
            .onEach { result ->
                if (result is Result.Error) {
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

    fun sendComment(
        content: String,
    ) {
        val videoId =
            selectedVideoId
                ?: return

        val participation =
            findParticipation(
                videoId = videoId,
            ) ?: return

        if (!participation.commentEnabled) {
            return
        }

        val trimmedContent =
            content.trim()

        if (
            trimmedContent.isEmpty() ||
            trimmedContent.length >
            MAX_COMMENT_LENGTH
        ) {
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
            .createVideoComment(
                videoId =
                    videoId,
                content =
                    trimmedContent,
                parentCommentId =
                    parentCommentId,
            )
            .asResult()
            .onEach { result ->
                if (result !is Result.Success) {
                    return@onEach
                }

                incrementParticipationCommentCount(
                    videoId = videoId,
                )

                if (parentCommentId == null) {
                    loadVideoComments(
                        videoId = videoId,
                    )
                } else {
                    incrementReplyCount(
                        commentId =
                            parentCommentId,
                    )

                    loadReplies(
                        commentId =
                            parentCommentId,
                    )
                }
            }
            .launchIn(
                viewModelScope,
            )
    }

    private fun loadEntry(
        entry: Entry,
    ) {
        entryLoadJob?.cancel()
        feedLoadJob?.cancel()

        _uiState.value =
            ChallengeParticipationUiState.Loading

        if (entry.initialVideoId != null) {
            loadInitialParticipation(
                challengeId =
                    entry.challengeId,
                videoId =
                    entry.initialVideoId,
            )
        } else {
            val challengeId =
                entry.challengeId
                    ?: return

            loadParticipationFeed(
                challengeId =
                    challengeId,
                initialParticipation =
                    null,
            )
        }
    }

    private fun loadInitialParticipation(
        challengeId: Long?,
        videoId: Long,
    ) {
        entryLoadJob =
            challengeRepository
                .getChallengeParticipation(
                    id = videoId,
                )
                .asResult()
                .onEach { result ->
                    when (result) {
                        Result.Loading -> {
                            _uiState.value =
                                ChallengeParticipationUiState
                                    .Loading
                        }

                        is Result.Success -> {
                            val participation =
                                result.data

                            if (
                                challengeId != null &&
                                challengeId !=
                                participation.challengeId
                            ) {
                                _uiState.value =
                                    ChallengeParticipationUiState
                                        .Error(
                                            exception =
                                                IllegalStateException(
                                                    "Challenge participation does not belong to the requested challenge.",
                                                ),
                                        )

                                return@onEach
                            }

                            loadParticipationFeed(
                                challengeId =
                                    participation
                                        .challengeId,
                                initialParticipation =
                                    participation,
                            )
                        }

                        is Result.Error -> {
                            _uiState.value =
                                ChallengeParticipationUiState
                                    .Error(
                                        exception =
                                            result.exception,
                                    )
                        }
                    }
                }
                .launchIn(
                    viewModelScope,
                )
    }

    private fun loadParticipationFeed(
        challengeId: Long,
        initialParticipation:
        ChallengeParticipation?,
    ) {
        feedLoadJob?.cancel()

        feedLoadJob =
            challengeRepository
                .getChallengeParticipationFeed(
                    challengeId =
                        challengeId,
                    cursor =
                        null,
                    size =
                        PARTICIPATION_FEED_SIZE,
                )
                .asResult()
                .onEach { result ->
                    when (result) {
                        Result.Loading -> {
                            _uiState.value =
                                ChallengeParticipationUiState
                                    .Loading
                        }

                        is Result.Success -> {
                            val participations =
                                mergeInitialParticipation(
                                    initialParticipation =
                                        initialParticipation,
                                    participations =
                                        result.data.items,
                                )

                            if (participations.isEmpty()) {
                                _uiState.value =
                                    ChallengeParticipationUiState
                                        .Empty(
                                            challengeId =
                                                challengeId,
                                        )

                                return@onEach
                            }

                            val initialPage =
                                initialParticipation
                                    ?.let { initial ->
                                        participations
                                            .indexOfFirst {
                                                    participation ->
                                                participation.id ==
                                                        initial.id
                                            }
                                            .takeIf {
                                                it >= 0
                                            }
                                    }
                                    ?: 0

                            _uiState.value =
                                ChallengeParticipationUiState
                                    .Success(
                                        challengeId =
                                            challengeId,
                                        participations =
                                            participations,
                                        initialPage =
                                            initialPage,
                                        nextCursor =
                                            result.data
                                                .nextCursor,
                                        hasNext =
                                            result.data
                                                .hasNext,
                                    )
                        }

                        is Result.Error -> {
                            _uiState.value =
                                ChallengeParticipationUiState
                                    .Error(
                                        exception =
                                            result.exception,
                                    )
                        }
                    }
                }
                .launchIn(
                    viewModelScope,
                )
    }

    private fun loadNextParticipationPage(
        challengeId: Long,
        cursor: String,
    ) {
        _uiState.update { state ->
            if (
                state !is
                        ChallengeParticipationUiState.Success
            ) {
                return@update state
            }

            state.copy(
                appendState =
                    ChallengeParticipationAppendUiState
                        .Loading,
            )
        }

        feedLoadJob?.cancel()

        feedLoadJob =
            challengeRepository
                .getChallengeParticipationFeed(
                    challengeId =
                        challengeId,
                    cursor =
                        cursor,
                    size =
                        PARTICIPATION_FEED_SIZE,
                )
                .asResult()
                .onEach { result ->
                    when (result) {
                        Result.Loading -> Unit

                        is Result.Success -> {
                            _uiState.update { state ->
                                if (
                                    state !is
                                            ChallengeParticipationUiState.Success
                                ) {
                                    return@update state
                                }

                                state.copy(
                                    participations =
                                        (
                                                state.participations +
                                                        result.data.items
                                                )
                                            .distinctBy {
                                                    participation ->
                                                participation.id
                                            },
                                    nextCursor =
                                        result.data.nextCursor,
                                    hasNext =
                                        result.data.hasNext,
                                    appendState =
                                        ChallengeParticipationAppendUiState
                                            .Idle,
                                )
                            }
                        }

                        is Result.Error -> {
                            _uiState.update { state ->
                                if (
                                    state !is
                                            ChallengeParticipationUiState.Success
                                ) {
                                    return@update state
                                }

                                state.copy(
                                    appendState =
                                        ChallengeParticipationAppendUiState
                                            .Error(
                                                exception =
                                                    result.exception,
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

    private fun mergeInitialParticipation(
        initialParticipation:
        ChallengeParticipation?,
        participations:
        List<ChallengeParticipation>,
    ): List<ChallengeParticipation> {
        if (initialParticipation == null) {
            return participations
        }

        if (
            participations.any {
                    participation ->
                participation.id ==
                        initialParticipation.id
            }
        ) {
            return participations
        }

        return listOf(
            initialParticipation,
        ) + participations
    }

    private fun findParticipation(
        videoId: Long,
    ): ChallengeParticipation? {
        val state =
            _uiState.value
                    as? ChallengeParticipationUiState.Success
                ?: return null

        return state.participations
            .firstOrNull {
                    participation ->
                participation.id ==
                        videoId
            }
    }

    private fun updateParticipationLikeState(
        videoId: Long,
        isLiked: Boolean,
        likeCount: Long,
    ) {
        _uiState.update { state ->
            if (
                state !is
                        ChallengeParticipationUiState.Success
            ) {
                return@update state
            }

            state.copy(
                participations =
                    state.participations
                        .map { participation ->
                            if (
                                participation.id ==
                                videoId
                            ) {
                                participation.copy(
                                    isLiked =
                                        isLiked,
                                    likeCount =
                                        likeCount,
                                )
                            } else {
                                participation
                            }
                        },
            )
        }
    }

    private fun rollbackParticipationLike(
        videoId: Long,
        targetLiked: Boolean,
        previousLiked: Boolean,
        previousLikeCount: Long,
    ) {
        val participation =
            findParticipation(
                videoId = videoId,
            ) ?: return

        if (
            participation.isLiked !=
            targetLiked
        ) {
            return
        }

        updateParticipationLikeState(
            videoId = videoId,
            isLiked = previousLiked,
            likeCount = previousLikeCount,
        )
    }

    private fun incrementParticipationCommentCount(
        videoId: Long,
    ) {
        _uiState.update { state ->
            if (
                state !is
                        ChallengeParticipationUiState.Success
            ) {
                return@update state
            }

            state.copy(
                participations =
                    state.participations
                        .map { participation ->
                            if (
                                participation.id ==
                                videoId
                            ) {
                                participation.copy(
                                    commentCount =
                                        participation
                                            .commentCount + 1,
                                )
                            } else {
                                participation
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
        _commentUiState.update { state ->
            val comments =
                state.comments
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
                state.replies
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
                        state.page
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
        val comment =
            findComment(
                commentId = commentId,
            ) ?: return

        if (
            comment.isLiked !=
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
                state.comments
                    .map { comment ->
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
                                                        1,
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

    private data class Entry(
        val challengeId: Long?,
        val initialVideoId: Long?,
    )

    private companion object {
        const val PARTICIPATION_FEED_SIZE =
            20

        const val MAX_COMMENT_LENGTH =
            1_000
    }
}