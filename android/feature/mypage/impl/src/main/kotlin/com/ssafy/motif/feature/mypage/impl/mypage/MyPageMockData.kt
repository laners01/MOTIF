package com.ssafy.motif.feature.mypage.impl.mypage

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
}
