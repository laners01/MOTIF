package com.ssafy.motif.feature.artistdetail.impl.artistdetail

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class ArtistDetailViewModel @Inject constructor() : ViewModel() {


    fun selectTab(tab: ArtistDetailTab){
        _uiState.update { state -> state.copy(selectedTab = tab) }
    }
    private val _uiState = MutableStateFlow(
        ArtistDetailUiState(
            artistDetail = ArtistDetailMockData.artist,
            notices = ArtistDetailMockData.notices,
            challengeState = ChallengeUiState.Success(
                challenges = ArtistDetailMockData.challenges.take(CHALLENGE_PAGE_SIZE),
                hasNext = ArtistDetailMockData.challenges.size>CHALLENGE_PAGE_SIZE,
                ),
            ),
        )
    internal val uiState: StateFlow<ArtistDetailUiState> = _uiState.asStateFlow()

    fun toggleSubscribe() {
        _uiState.update { state ->
            val detail = state.artistDetail ?: return@update state
            state.copy(
                artistDetail = detail.copy(
                    isSubscribed = !detail.isSubscribed,
                    subscribeNum = detail.subscribeNum + if (detail.isSubscribed) -1 else 1,
                ),
            )
        }
    }

    fun toggleNoticeLike(noticeId: Long) {
        _uiState.update { state ->
            state.copy(
                notices = state.notices.map{notice ->
                    if(notice.id != noticeId){
                        notice
                    }else{
                        notice.copy(
                            isLiked = !notice.isLiked,
                            likeCount = notice.likeCount + if (notice.isLiked) -1 else 1,
                        )
                    }
                }
            )
        }
    }

    fun onNoticeCommentClick(noticeId: Long) {
        val notice = _uiState.value.notices.find { it.id == noticeId }?:return
        _uiState.update{state ->
            state.copy(
                commentSheet = CommentSheetUiState.Success(
                    commentCount = notice.commentCount,
                    comments = ArtistDetailMockData.comments,
                    repliesByCommentId = ArtistDetailMockData.replies,
                    expandedCommentIds = emptySet(),
                )
            )
        }
    }

    fun onCommentSheetDismiss(){
        _uiState.update { it.copy(commentSheet =
            CommentSheetUiState.Hidden) }
    }

    fun onCommentLikeClick(commentId: Long) { }
    fun onReplyClick(commentId: Long) { }      // expandedCommentIds 토글
    fun onReplyLikeClick(replyId: Long) { }

    fun toggleAlarm(){
        _uiState.update { state ->
            val detail = state.artistDetail ?: return@update state
            state.copy(
                artistDetail = detail.copy(
                    isAlarmOn = !detail.isAlarmOn,
                ),
            )
        }
    }

    fun onChallengeClick(challengeId: Long){

    }
    fun goBackScreen(){

    }

    companion object{
        private const val CHALLENGE_PAGE_SIZE = 9
    }

}
