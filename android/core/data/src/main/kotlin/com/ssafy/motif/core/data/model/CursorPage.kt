package com.ssafy.motif.core.data.model

data class CursorPage<T>(
    val items: List<T>,
    val previousCursor: String?,
    val nextCursor: String?,
    val hasPrevious: Boolean,
    val hasNext: Boolean,
)