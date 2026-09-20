package com.ssafy.motif.core.network.service.comment.model

import kotlinx.serialization.Serializable

@Serializable
data class AuthorResponse(
    val userSeq: Int,
    val nickname: String,
    val profileImageUrl: String? = null,
    val isArtist: Boolean,
)

@Serializable
data class CommentItemResponse(
    val commentSeq: Long,
    val content: String,
    val author: AuthorResponse,
    val likeCount: Int,
    val isLiked: Boolean,
    /** 접힌 답글 수. 노출되는 답글만 센다. */
    val replyCount: Long,
    val isMine: Boolean,
    /** ISO-8601 원시각. 상대 시간 표기는 상위 계층에서 만든다. */
    val regDttm: String,
)

@Serializable
data class ReplyItemResponse(
    val commentSeq: Long,
    val content: String,
    val author: AuthorResponse,
    val likeCount: Int,
    val isLiked: Boolean,
    val isMine: Boolean,
    /** ISO-8601 원시각. 상대 시간 표기는 상위 계층에서 만든다. */
    val regDttm: String,
)

@Serializable
data class CommentCreateResponse(
    val commentSeq: Long,
)

@Serializable
data class CommentDeleteResponse(
    val deletedCount: Int,
)
