package com.ssafy.motif.core.model.artist

import com.ssafy.motif.core.model.motif.Motif
import com.ssafy.motif.core.model.notice.Notice

sealed interface ArtistFeedItem {

    val createdAt: Long

    data class MotifAsset(
        val motif: Motif,
        val durationMs: Long,
    ) : ArtistFeedItem {

        override val createdAt: Long
            get() = motif.createdAt
    }

    data class NoticeItem(
        val notice: Notice,
    ) : ArtistFeedItem {

        override val createdAt: Long
            get() = notice.createdAt
    }
}