package com.ssafy.motif.core.data.repository

import com.ssafy.motif.core.data.model.OffsetPage
import com.ssafy.motif.core.model.comment.Comment
import com.ssafy.motif.core.model.comment.CommentType
import kotlinx.coroutines.flow.Flow

interface CommentRepository {

    fun getNoticeComments(
        noticeId: Long,
        page: Int = DEFAULT_PAGE,
        size: Int = DEFAULT_PAGE_SIZE,
    ): Flow<OffsetPage<Comment>>

    fun createNoticeComment(
        noticeId: Long,
        content: String,
        parentCommentId: Long? = null,
    ): Flow<Long>

    fun getReplies(
        type: CommentType,
        commentId: Long,
        page: Int = DEFAULT_PAGE,
        size: Int = DEFAULT_PAGE_SIZE,
    ): Flow<OffsetPage<Comment>>

    fun deleteComment(
        type: CommentType,
        commentId: Long,
    ): Flow<Unit>

    fun updateCommentLike(
        type: CommentType,
        commentId: Long,
        isLiked: Boolean,
    ): Flow<Unit>

    private companion object {
        const val DEFAULT_PAGE = 0
        const val DEFAULT_PAGE_SIZE = 20
    }
}