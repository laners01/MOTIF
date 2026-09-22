package com.ssafy.motif.core.data.mapper

import com.ssafy.motif.core.data.model.OffsetPage
import com.ssafy.motif.core.model.comment.Comment
import com.ssafy.motif.core.model.comment.CommentAuthorType
import com.ssafy.motif.core.network.model.PageResponse
import com.ssafy.motif.core.network.model.comment.CommentItemResponse
import com.ssafy.motif.core.network.model.comment.ReplyItemResponse
import java.time.Instant

internal fun PageResponse<CommentItemResponse>.toCommentPage(): OffsetPage<Comment> =
    OffsetPage(
        items = list.map { response ->
            response.toModel()
        },
        page = page,
        size = size,
        totalElements = totalElements,
        totalPages = totalPages,
        hasNext = hasNext,
        hasPrevious = hasPrevious,
    )

internal fun PageResponse<ReplyItemResponse>.toReplyPage(): OffsetPage<Comment> =
    OffsetPage(
        items = list.map { response ->
            response.toModel()
        },
        page = page,
        size = size,
        totalElements = totalElements,
        totalPages = totalPages,
        hasNext = hasNext,
        hasPrevious = hasPrevious,
    )

private fun CommentItemResponse.toModel(): Comment =
    Comment(
        id = commentSeq,
        profileImageUrl = author.profileImageUrl.orEmpty(),
        nickname = author.nickname,
        content = content,
        createdAt = regDttm.toEpochMilli(),
        likeCount = likeCount.toLong(),
        replyCount = replyCount,
        isLiked = isLiked,
        authorType = resolveAuthorType(
            isArtist = author.isArtist,
            isMine = isMine,
        ),
    )

private fun ReplyItemResponse.toModel(): Comment =
    Comment(
        id = commentSeq,
        profileImageUrl = author.profileImageUrl.orEmpty(),
        nickname = author.nickname,
        content = content,
        createdAt = regDttm.toEpochMilli(),
        likeCount = likeCount.toLong(),
        replyCount = 0L,
        isLiked = isLiked,
        authorType = resolveAuthorType(
            isArtist = author.isArtist,
            isMine = isMine,
        ),
    )

private fun resolveAuthorType(
    isArtist: Boolean,
    isMine: Boolean,
): CommentAuthorType =
    when {
        isArtist -> CommentAuthorType.ARTIST
        isMine -> CommentAuthorType.CURRENT_USER
        else -> CommentAuthorType.USER
    }

private fun String.toEpochMilli(): Long =
    runCatching {
        Instant.parse(this).toEpochMilli()
    }.getOrDefault(0L)