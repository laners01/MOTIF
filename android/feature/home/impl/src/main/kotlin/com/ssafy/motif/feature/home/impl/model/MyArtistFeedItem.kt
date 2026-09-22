package com.ssafy.motif.feature.home.impl.model

import com.ssafy.motif.core.model.artist.ArtistFeedItem
import com.ssafy.motif.core.model.motif.Motif
import com.ssafy.motif.core.model.notice.Notice

internal sealed interface MyArtistFeedItem {

    val createdAt: Long

    data class NoticeItem(
        val notice: Notice,
    ) : MyArtistFeedItem {

        override val createdAt: Long
            get() = notice.createdAt
    }

    data class MotifItem(
        val motif: Motif,
    ) : MyArtistFeedItem {

        override val createdAt: Long
            get() = motif.createdAt
    }
}

internal fun ArtistFeedItem.toMyArtistFeedItem(): MyArtistFeedItem =
    when (this) {
        is ArtistFeedItem.MotifAsset -> {
            MyArtistFeedItem.MotifItem(
                motif = motif,
            )
        }

        is ArtistFeedItem.NoticeItem -> {
            MyArtistFeedItem.NoticeItem(
                notice = notice,
            )
        }
    }