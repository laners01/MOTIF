package com.ssafy.motif.feature.artistdetail.impl.artistdetail

import com.ssafy.motif.core.model.artistdetail.ArtistDetail

internal object ArtistDetailMockData {

    val artist: ArtistDetail = ArtistDetail(
        name = "Yeji",
        groupName = "ITZY",
        profileImageUrl = null,
        introducing = "요즘 시간이 진짜 빨리 가는 것 같아요.\n그래도 곧 재밌는 거 보여줄 수 있을 것 같아요 :)",
        subscribeNum = 2_100_000L,
        challengeNum = 121L,
        isAlarmOn = true,
        isSubscribed = false,
    )
}
