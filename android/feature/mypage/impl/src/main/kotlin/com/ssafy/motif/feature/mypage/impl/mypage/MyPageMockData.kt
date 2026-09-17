package com.ssafy.motif.feature.mypage.impl.mypage

import com.ssafy.motif.core.model.mypage.MyChallenge
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
}
