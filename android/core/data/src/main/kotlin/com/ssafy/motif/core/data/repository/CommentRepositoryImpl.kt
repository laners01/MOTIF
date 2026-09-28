package com.ssafy.motif.core.data.repository

import com.ssafy.motif.core.common.network.Dispatcher
import com.ssafy.motif.core.common.network.MotifDispatcher
import com.ssafy.motif.core.data.mapper.toCommentPage
import com.ssafy.motif.core.data.mapper.toReplyPage
import com.ssafy.motif.core.data.model.OffsetPage
import com.ssafy.motif.core.model.comment.Comment
import com.ssafy.motif.core.model.comment.CommentType
import com.ssafy.motif.core.network.service.comment.CommentClient
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject

internal class CommentRepositoryImpl @Inject constructor(
    private val commentClient: CommentClient,
    @param:Dispatcher(MotifDispatcher.IO)
    private val ioDispatcher: CoroutineDispatcher,
) : CommentRepository {

    override fun getNoticeComments(
        noticeId: Long,
        page: Int,
        size: Int,
    ): Flow<OffsetPage<Comment>> =
        flow {
            val response =
                commentClient.getNoticeComments(
                    noticeId = noticeId,
                    page = page,
                    size = size,
                )

            emit(
                response.toCommentPage(),
            )
        }.flowOn(
            ioDispatcher,
        )

    override fun createNoticeComment(
        noticeId: Long,
        content: String,
        parentCommentId: Long?,
    ): Flow<Long> =
        flow {
            val response =
                commentClient.createNoticeComment(
                    noticeId = noticeId,
                    content = content,
                    parentCommentId =
                        parentCommentId,
                )

            emit(
                response.commentSeq,
            )
        }.flowOn(
            ioDispatcher,
        )

    override fun getChallengeComments(
        challengeId: Long,
        page: Int,
        size: Int,
    ): Flow<OffsetPage<Comment>> =
        flow {
            val response =
                commentClient.getChallengeComments(
                    challengeId =
                        challengeId,
                    page =
                        page,
                    size =
                        size,
                )

            emit(
                response.toCommentPage(),
            )
        }.flowOn(
            ioDispatcher,
        )

    override fun createChallengeComment(
        challengeId: Long,
        content: String,
        parentCommentId: Long?,
    ): Flow<Long> =
        flow {
            val response =
                commentClient.createChallengeComment(
                    challengeId =
                        challengeId,
                    content =
                        content,
                    parentCommentId =
                        parentCommentId,
                )

            emit(
                response.commentSeq,
            )
        }.flowOn(
            ioDispatcher,
        )

    override fun getVideoComments(
        videoId: Long,
        page: Int,
        size: Int,
    ): Flow<OffsetPage<Comment>> =
        flow {
            val response =
                commentClient.getVideoComments(
                    videoId =
                        videoId,
                    page =
                        page,
                    size =
                        size,
                )

            emit(
                response.toCommentPage(),
            )
        }.flowOn(
            ioDispatcher,
        )

    override fun createVideoComment(
        videoId: Long,
        content: String,
        parentCommentId: Long?,
    ): Flow<Long> =
        flow {
            val response =
                commentClient.createVideoComment(
                    videoId =
                        videoId,
                    content =
                        content,
                    parentCommentId =
                        parentCommentId,
                )

            emit(
                response.commentSeq,
            )
        }.flowOn(
            ioDispatcher,
        )

    override fun getReplies(
        type: CommentType,
        commentId: Long,
        page: Int,
        size: Int,
    ): Flow<OffsetPage<Comment>> =
        flow {
            val response =
                commentClient.getReplies(
                    type =
                        type,
                    commentId =
                        commentId,
                    page =
                        page,
                    size =
                        size,
                )

            emit(
                response.toReplyPage(),
            )
        }.flowOn(
            ioDispatcher,
        )

    override fun deleteComment(
        type: CommentType,
        commentId: Long,
    ): Flow<Unit> =
        flow {
            commentClient.deleteComment(
                type =
                    type,
                commentId =
                    commentId,
            )

            emit(Unit)
        }.flowOn(
            ioDispatcher,
        )

    override fun updateCommentLike(
        type: CommentType,
        commentId: Long,
        isLiked: Boolean,
    ): Flow<Unit> =
        flow {
            commentClient.updateCommentLike(
                type =
                    type,
                commentId =
                    commentId,
                isLiked =
                    isLiked,
            )

            emit(Unit)
        }.flowOn(
            ioDispatcher,
        )
}