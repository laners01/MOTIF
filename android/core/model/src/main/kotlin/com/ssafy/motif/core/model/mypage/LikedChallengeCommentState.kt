package com.ssafy.motif.core.model.mypage

sealed interface LikedChallengeCommentState{
    /** 영상 삭제, 댓글 사용 중지, 원본 댓글 삭제. 영역 자체를 노출하지 않는다. */
    data object Hidden: LikedChallengeCommentState
    /** 댓글을 쓸 수 있지만 작성한 댓글이 없다. */
    data object Empty: LikedChallengeCommentState

    data class Written(
        val myComment: LikedChallengeComment,
    ): LikedChallengeCommentState
}