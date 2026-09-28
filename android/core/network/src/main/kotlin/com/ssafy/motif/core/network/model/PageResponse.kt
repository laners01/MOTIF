package com.ssafy.motif.core.network.model

import kotlinx.serialization.Serializable

@Serializable
data class PageResponse<T>(
    val list: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
    val hasNext: Boolean,
    val hasPrevious: Boolean,
)
