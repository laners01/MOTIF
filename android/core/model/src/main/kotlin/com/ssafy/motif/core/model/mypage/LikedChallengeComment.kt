package com.ssafy.motif.core.model.mypage

import com.ssafy.motif.core.model.comment.Comment
import com.ssafy.motif.core.model.comment.Reply

data class LikedChallengeComment (
    val comment: Comment,
    val replies: List<Reply>,
)