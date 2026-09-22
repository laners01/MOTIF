package com.ssafy.motif.core.network.service.comment

import com.ssafy.motif.core.model.comment.CommentType
import com.ssafy.motif.core.network.model.PageResponse
import com.ssafy.motif.core.network.model.comment.CommentCreateRequest
import com.ssafy.motif.core.network.model.comment.CommentCreateResponse
import com.ssafy.motif.core.network.model.comment.CommentDeleteResponse
import com.ssafy.motif.core.network.model.comment.CommentItemResponse
import com.ssafy.motif.core.network.model.comment.ReplyItemResponse
import javax.inject.Inject

class CommentClient @Inject constructor(
    private val commentService: CommentService,
) {

    suspend fun getNoticeComments(
        noticeId: Long,
        page: Int = DEFAULT_PAGE,
        size: Int = DEFAULT_SIZE,
    ): PageResponse<CommentItemResponse> =
        commentService.getNoticeComments(
            noticeSeq = noticeId,
            page = page,
            size = size,
        ).data

    suspend fun createNoticeComment(
        noticeId: Long,
        content: String,
        parentCommentId: Long? = null,
    ): CommentCreateResponse =
        commentService.postNoticeComment(
            noticeSeq = noticeId,
            request = CommentCreateRequest(
                content = content,
                parentCommentSeq = parentCommentId,
            ),
        ).data

    suspend fun getReplies(
        type: CommentType,
        commentId: Long,
        page: Int = DEFAULT_PAGE,
        size: Int = DEFAULT_SIZE,
    ): PageResponse<ReplyItemResponse> =
        commentService.getReplies(
            type = type.value,
            commentSeq = commentId,
            page = page,
            size = size,
        ).data

    suspend fun deleteComment(
        type: CommentType,
        commentId: Long,
    ): CommentDeleteResponse =
        commentService.deleteComment(
            type = type.value,
            commentSeq = commentId,
        ).data

    suspend fun updateCommentLike(
        type: CommentType,
        commentId: Long,
        isLiked: Boolean,
    ) {
        if (isLiked) {
            commentService.postCommentLike(
                type = type.value,
                commentSeq = commentId,
            )
        } else {
            commentService.deleteCommentLike(
                type = type.value,
                commentSeq = commentId,
            )
        }
    }

    private companion object {
        const val DEFAULT_PAGE = 0
        const val DEFAULT_SIZE = 20
    }
}