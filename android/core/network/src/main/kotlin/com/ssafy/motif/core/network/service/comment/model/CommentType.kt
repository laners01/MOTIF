package com.ssafy.motif.core.network.service.comment.model

/**
 * 댓글 공통 API 경로의 `{type}`. 서버는 소문자만 받으므로 [value]를 경로에 전달한다.
 */
enum class CommentType(val value: String) {
    CHALLENGE("challenge"),
    VIDEO("video"),
    NOTICE("notice"),
}
