package com.ssafy.motif.core.data.repository

import kotlinx.coroutines.flow.Flow

interface NoticeRepository {

    fun updateNoticeLike(
        id: Long,
        isLiked: Boolean,
    ): Flow<Unit>
}