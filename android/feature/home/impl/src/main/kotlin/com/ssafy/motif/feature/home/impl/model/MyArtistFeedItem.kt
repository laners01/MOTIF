package com.ssafy.motif.feature.home.impl.model

import com.ssafy.motif.core.model.motif.Motif
import com.ssafy.motif.core.model.home.Notice

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

internal fun mergeMyArtistFeedItems(
    notices: List<Notice>,
    motifs: List<Motif>,
): List<MyArtistFeedItem> =
    buildList {
        addAll(
            notices.map { notice ->
                MyArtistFeedItem.NoticeItem(
                    notice = notice,
                )
            },
        )

        addAll(
            motifs.map { motif ->
                MyArtistFeedItem.MotifItem(
                    motif = motif,
                )
            },
        )
    }.sortedByDescending(
        MyArtistFeedItem::createdAt,
    )