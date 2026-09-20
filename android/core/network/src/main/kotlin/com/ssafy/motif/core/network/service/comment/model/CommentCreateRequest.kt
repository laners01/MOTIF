package com.ssafy.motif.core.network.service.comment.model

import kotlinx.serialization.Serializable

@Serializable
data class CommentCreateRequest(
    /** 본문. 최대 1000자. */
    val content: String,
    /** 답글일 때만 담는다. null이면 직렬화에서 제외되어 루트 댓글로 작성된다. */
    val parentCommentSeq: Long? = null,
)
