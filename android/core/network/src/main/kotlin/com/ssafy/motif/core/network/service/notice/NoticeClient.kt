package com.ssafy.motif.core.network.service.notice

import javax.inject.Inject

class NoticeClient @Inject constructor(
    private val noticeService: NoticeService,
) {

    suspend fun updateNoticeLike(
        noticeId: Long,
        isLiked: Boolean,
    ) {
        if (isLiked) {
            noticeService.postNoticeLike(
                noticeSeq = noticeId,
            )
        } else {
            noticeService.deleteNoticeLike(
                noticeSeq = noticeId,
            )
        }
    }
}