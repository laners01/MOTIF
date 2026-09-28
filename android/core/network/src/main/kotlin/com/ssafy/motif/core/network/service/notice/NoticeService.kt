package com.ssafy.motif.core.network.service.notice

import retrofit2.http.DELETE
import retrofit2.http.POST
import retrofit2.http.Path

interface NoticeService {

    @POST("api/v1/notices/{noticeSeq}/like")
    suspend fun postNoticeLike(
        @Path("noticeSeq") noticeSeq: Long,
    )

    @DELETE("api/v1/notices/{noticeSeq}/like")
    suspend fun deleteNoticeLike(
        @Path("noticeSeq") noticeSeq: Long,
    )
}