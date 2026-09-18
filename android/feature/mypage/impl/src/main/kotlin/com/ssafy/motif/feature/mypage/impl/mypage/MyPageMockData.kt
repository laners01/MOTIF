package com.ssafy.motif.feature.mypage.impl.mypage

import com.ssafy.motif.core.model.mypage.MyChallenge
import com.ssafy.motif.core.model.mypage.MyMotif
import com.ssafy.motif.core.model.mypage.SubscribedArtist
import com.ssafy.motif.core.model.mypage.UserProfile

internal object MyPageMockData {

    val profile: UserProfile = UserProfile(
        id = 1L,
        nickname = "midzy_01",
        // 미등록 상태 확인용. 이미지가 있는 경우를 보려면 PROFILE_IMAGE로 교체
        profileImageUrl = null,
    )

    const val PROFILE_IMAGE =
        "android.resource://com.ssafy.motif/drawable/profile_square"

    private val CHALLENGE_VIEWS = listOf(
        820_000L, 143_000L, 28_400L, 9_700L, 2_300L, 640L, 88L, 12L,
    )

    private const val CHALLENGE_BASE_TIME = 1_757_900_000_000L
    private const val ONE_DAY_MILLIS = 86_400_000L

    // index가 커질수록 createdAt이 작아지므로 목록 자체가 최신 업로드순이다.
    val challenges: List<MyChallenge> = List(23) { index ->
        MyChallenge(
            id = index + 1L,
            // TODO 마이페이지 전용 썸네일로 교체. 현재는 artistdetail 모듈 리소스를 빌려 씀
            thumbnailUrl = "android.resource://com.ssafy.motif/drawable/ch_${index % 5 + 1}",
            view = CHALLENGE_VIEWS[index % CHALLENGE_VIEWS.size],
            createdAt = CHALLENGE_BASE_TIME - index * ONE_DAY_MILLIS,
        )
    }

    // 패스 보유/미보유, 이미지 유무, 수치 0인 경우를 모두 포함한다.
    val subscribedArtists: List<SubscribedArtist> = listOf(
        SubscribedArtist(
            id = 1L,
            name = "KARINA",
            profileImageUrl = "android.resource://com.ssafy.motif/drawable/yeji",
            hasMotifPass = true,
            subscribedDays = 421,
            challengeCount = 8L,
            ownedMotifCount = 12L,
            totalMotifCount = 28L,
        ),
        SubscribedArtist(
            id = 2L,
            name = "Ryujin",
            profileImageUrl = PROFILE_IMAGE,
            hasMotifPass = false,
            subscribedDays = 42,
            challengeCount = 0L,
            ownedMotifCount = 0L,
            totalMotifCount = 15L,
        ),
        SubscribedArtist(
            id = 3L,
            name = "Lia",
            profileImageUrl = PROFILE_IMAGE,
            hasMotifPass = true,
            subscribedDays = 128,
            challengeCount = 5L,
            ownedMotifCount = 7L,
            totalMotifCount = 7L,
        ),
        SubscribedArtist(
            id = 4L,
            name = "Chaeryeong",
            // 이미지 미등록. 기본 프로필 노출 확인용
            profileImageUrl = null,
            hasMotifPass = false,
            subscribedDays = 7,
            challengeCount = 2L,
            ownedMotifCount = 1L,
            totalMotifCount = 9L,
        ),
        SubscribedArtist(
            id = 5L,
            name = "Yuna",
            profileImageUrl = PROFILE_IMAGE,
            hasMotifPass = true,
            subscribedDays = 1_024,
            challengeCount = 37L,
            ownedMotifCount = 23L,
            totalMotifCount = 31L,
        ),
    )

    // 대표 모티프. 미설정 상태를 보려면 ViewModel에서 null을 내려주면 된다.
    val representativeMotif: MyMotif = MyMotif(
        id = 1L,
        // TODO 마이페이지 전용 리소스로 교체. 현재는 artistdetail 모듈 것을 빌려 씀
        videoUrl = "android.resource://com.ssafy.motif/raw/test_video",
        thumbnailUrl = "android.resource://com.ssafy.motif/drawable/motif_thumbnail",
    )
}
