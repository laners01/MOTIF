package com.ssafy.motif.feature.artistdetail.impl.artistdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.motif.core.model.comment.Comment
import com.ssafy.motif.core.ui.comment.CommentPage
import com.ssafy.motif.core.ui.comment.CommentUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class ArtistDetailViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(
        ArtistDetailUiState(
            artistDetail = ArtistDetailMockData.artist,
            notices = ArtistDetailMockData.notices,
            challengeState = ChallengeUiState.Loading,
            motifs = ArtistDetailMockData.motifs,
        ),
    )

    internal val uiState: StateFlow<ArtistDetailUiState> =
        _uiState.asStateFlow()

    init {
        loadChallenges()
    }

    fun selectTab(tab: ArtistDetailTab) {
        _uiState.update { state ->
            state.copy(
                selectedTab = tab,
            )
        }
    }

    fun toggleSubscribe() {
        _uiState.update { state ->
            val detail = state.artistDetail ?: return@update state

            state.copy(
                artistDetail = detail.copy(
                    isSubscribed = !detail.isSubscribed,
                    subscribeNum = detail.subscribeNum +
                            if (detail.isSubscribed) -1 else 1,
                ),
            )
        }
    }

    fun toggleAlarm() {
        _uiState.update { state ->
            val detail = state.artistDetail ?: return@update state

            state.copy(
                artistDetail = detail.copy(
                    isAlarmOn = !detail.isAlarmOn,
                ),
            )
        }
    }

    fun toggleNoticeLike(noticeId: Long) {
        _uiState.update { state ->
            state.copy(
                notices = state.notices.map { notice ->
                    if (notice.id != noticeId) {
                        notice
                    } else {
                        notice.copy(
                            isLiked = !notice.isLiked,
                            likeCount = notice.likeCount +
                                    if (notice.isLiked) -1 else 1,
                        )
                    }
                },
            )
        }
    }

    fun onNoticeCommentClick(noticeId: Long) {
        val exists = _uiState.value.notices.any { notice ->
            notice.id == noticeId
        }

        if (!exists) return

        _uiState.update { state ->
            state.copy(
                commentUiState = CommentUiState(
                    page = CommentPage.Comments,
                    isLoading = true,
                ),
            )
        }

        viewModelScope.launch {
            // TODO 댓글 조회 API 연동
            val comments = ArtistDetailMockData.comments

            _uiState.update { state ->
                state.copy(
                    commentUiState = state.commentUiState.copy(
                        comments = comments,
                        replies = emptyList(),
                        isLoading = false,
                    ),
                )
            }
        }
    }

    fun onReplyClick(commentId: Long) {
        val commentUiState = _uiState.value.commentUiState

        val parentComment = commentUiState.comments.firstOrNull { comment ->
            comment.id == commentId
        } ?: return

        _uiState.update { state ->
            state.copy(
                commentUiState = state.commentUiState.copy(
                    page = CommentPage.Replies(
                        parentComment = parentComment,
                    ),
                    replies = emptyList(),
                    isLoading = true,
                ),
            )
        }

        viewModelScope.launch {
            // TODO 답글 조회 API 연동
            val replies = ArtistDetailMockData.replies[commentId].orEmpty()

            _uiState.update { state ->
                state.copy(
                    commentUiState = state.commentUiState.copy(
                        replies = replies,
                        isLoading = false,
                    ),
                )
            }
        }
    }

    fun onCommentBackClick() {
        _uiState.update { state ->
            state.copy(
                commentUiState = state.commentUiState.copy(
                    page = CommentPage.Comments,
                    replies = emptyList(),
                    isLoading = false,
                ),
            )
        }
    }

    fun onCommentLikeClick(commentId: Long) {
        _uiState.update { state ->
            val commentUiState = state.commentUiState

            val comments = commentUiState.comments.map { comment ->
                if (comment.id == commentId) {
                    comment.toggleLike()
                } else {
                    comment
                }
            }

            val replies = commentUiState.replies.map { reply ->
                if (reply.id == commentId) {
                    reply.toggleLike()
                } else {
                    reply
                }
            }

            val page = when (val page = commentUiState.page) {
                CommentPage.Comments -> {
                    page
                }

                is CommentPage.Replies -> {
                    if (page.parentComment.id == commentId) {
                        page.copy(
                            parentComment = page.parentComment.toggleLike(),
                        )
                    } else {
                        page
                    }
                }
            }

            state.copy(
                commentUiState = commentUiState.copy(
                    page = page,
                    comments = comments,
                    replies = replies,
                ),
            )
        }
    }

    fun onCommentSendClick(content: String) {
        if (content.isBlank()) return

        // TODO 댓글 / 답글 작성 API 연동
    }

    fun onCreateMotifClick() {
        // TODO MOTIF 생성 화면 이동
    }

    fun onLoadMoreChallenges() {
        val current =
            _uiState.value.challengeState as? ChallengeUiState.Success
                ?: return

        if (!current.hasNext) return
        if (current.appendState !is ChallengeAppendUiState.Idle) return

        viewModelScope.launch {
            _uiState.update { state ->
                state.copy(
                    challengeState = current.copy(
                        appendState = ChallengeAppendUiState.Loading,
                    ),
                )
            }

            val loaded = current.challenges.size

            val next = ArtistDetailMockData.challenges
                .drop(loaded)
                .take(CHALLENGE_PAGE_SIZE)

            _uiState.update { state ->
                state.copy(
                    challengeState = ChallengeUiState.Success(
                        challenges = current.challenges + next,
                        hasNext = loaded + next.size <
                                ArtistDetailMockData.challenges.size,
                    ),
                )
            }
        }
    }

    fun onChallengeClick(challengeId: Long) {
    }

    fun goBackScreen() {
    }

    private fun loadChallenges() {
        viewModelScope.launch {
            _uiState.update { state ->
                state.copy(
                    challengeState = ChallengeUiState.Loading,
                )
            }

            val firstPage = ArtistDetailMockData.challenges
                .take(CHALLENGE_PAGE_SIZE)

            _uiState.update { state ->
                state.copy(
                    challengeState = ChallengeUiState.Success(
                        challenges = firstPage,
                        hasNext = firstPage.size <
                                ArtistDetailMockData.challenges.size,
                    ),
                )
            }
        }
    }

    private fun Comment.toggleLike(): Comment =
        copy(
            isLiked = !isLiked,
            likeCount = likeCount +
                    if (isLiked) -1 else 1,
        )

    companion object {
        private const val CHALLENGE_PAGE_SIZE = 9
    }
}