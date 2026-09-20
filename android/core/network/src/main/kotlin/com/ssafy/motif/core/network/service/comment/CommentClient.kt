package com.ssafy.motif.core.network.service.comment

import com.ssafy.motif.core.network.exception.runCatchingApi
import com.ssafy.motif.core.network.model.PageResponse
import com.ssafy.motif.core.network.service.comment.model.CommentCreateRequest
import com.ssafy.motif.core.network.service.comment.model.CommentItemResponse
import com.ssafy.motif.core.network.service.comment.model.CommentType
import com.ssafy.motif.core.network.service.comment.model.ReplyItemResponse
import kotlinx.serialization.json.Json
import javax.inject.Inject

class CommentClient @Inject constructor(
    private val commentService: CommentService,
    private val json: Json,
) {

    suspend fun getNoticeComments(
        noticeId: Long,
        page: Int = DEFAULT_PAGE,
        size: Int = DEFAULT_SIZE,
    ): PageResponse<CommentItemResponse> =
        runCatchingApi(json) {
            commentService
                .getNoticeComments(
                    noticeSeq = noticeId,
                    page = page,
                    size = size.coerceAtMost(MAX_SIZE),
                ).data
        }

    suspend fun createNoticeComment(
        noticeId: Long,
        content: String,
        parentCommentId: Long? = null,
    ): Long =
        runCatchingApi(json) {
            commentService
                .postNoticeComment(
                    noticeSeq = noticeId,
                    request =
                        CommentCreateRequest(
                            content = content,
                            parentCommentSeq = parentCommentId,
                        ),
                ).data.commentSeq
        }

    suspend fun getReplies(
        type: CommentType,
        commentId: Long,
        page: Int = DEFAULT_PAGE,
        size: Int = DEFAULT_SIZE,
    ): PageResponse<ReplyItemResponse> =
        runCatchingApi(json) {
            commentService
                .getReplies(
                    type = type.value,
                    commentSeq = commentId,
                    page = page,
                    size = size.coerceAtMost(MAX_SIZE),
                ).data
        }

    suspend fun deleteComment(
        type: CommentType,
        commentId: Long,
    ): Int =
        runCatchingApi(json) {
            commentService
                .deleteComment(
                    type = type.value,
                    commentSeq = commentId,
                ).data.deletedCount
        }

    suspend fun updateCommentLike(
        type: CommentType,
        commentId: Long,
        isLiked: Boolean,
    ) {
        runCatchingApi(json) {
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
    }

    private companion object {
        const val DEFAULT_PAGE = 0
        const val DEFAULT_SIZE = 20
        const val MAX_SIZE = 100
    }
}
