package com.ssafy.motif.feature.artistdetail.impl.artistdetail

import com.ssafy.motif.core.model.artistdetail.ArtistDetail
import com.ssafy.motif.core.model.home.Artist
import com.ssafy.motif.core.model.home.Notice

internal object ArtistDetailMockData {

    private const val PROFILE_SQUARE =
        "android.resource://com.ssafy.motif/drawable/profile_square"

    private const val YEJI =
        "android.resource://com.ssafy.motif/drawable/yeji"

    val artist: ArtistDetail = ArtistDetail(
        name = "Yeji",
        groupName = "ITZY",
        profileImageUrl = YEJI,
        introducing = "요즘 시간이 진짜 빨리 가는 것 같아요.\n그래도 곧 재밌는 거 보여줄 수 있을 것 같아요 :)",
        subscribeNum = 2_100_000L,
        challengeNum = 121L,
        isAlarmOn = true,
        isSubscribed = false,
        hasMotifPass = false
    )
    private val noticeArtist = Artist(
        id = 1L,
        name = "Yeji",
        profileImageUrl = YEJI,
        groupName = "ITZY",
    )

    val notices: List<Notice> = listOf(
        Notice(
            id = 1L,
            artist = noticeArtist,
            content = "안녕하세요. 예지입니다.\n오늘 공연 와주셔서 감사했어요.",
            imageUrls = emptyList(),
            likeCount = 2_100L,
            commentCount = 1_100L,
            isLiked = false,
            createdAt = 0L,
        ),
        Notice(
            id = 2L,
            artist = noticeArtist,
            content = "안녕하세요. 예지입니다.\n" +
                    "오늘 공연 와주셔서 감사했어요.\n" +
                    "무대 위에서 객석을 바라봤을 때 익숙한 얼굴이 보여서 정말 반갑고, 마음이 놓였어요. 사실 공연 전에는 긴장이 많이 됐는데, 응원해주시는 분들이 계신다는 걸 생각하니 끝까지 힘을 낼 수 있었던 것 같아요.\n" +
                    "오늘 무대는 저에게도 의미가 컸어요. 준비하는 동안 힘든 순간도 많았지만, 이렇게 직접 와서 함께해주신 분들 덕분에 그 시간들이 다 보람으로 바뀐 기분이에요. 박수 소리, 환호 소리 하나하나가 오래 기억에 남을 것 같아요.\n" +
                    "바쁘신 와중에 시간 내주시고, 먼 길 와주신 것도 잊지 않을게요. 오늘 받은 마음 소중히 담아서 다음 무대는 더 좋은 모습으로 보여드리고 싶어요.\n" +
                    "돌아가시는 길 조심히 가시고, 편히 쉬세요.\n" +
                    "오늘 정말 고마웠어요. 또 만나요!\n" +
                    "예지 드림" ,
            imageUrls = listOf(YEJI,PROFILE_SQUARE),
            likeCount = 9_100L,
            commentCount = 1_100L,
            isLiked = false,
            createdAt = 0L,
        ),

    )
}
