package com.ssafy.motif.feature.motif.impl.assets

import com.ssafy.motif.core.model.artist.Artist
import com.ssafy.motif.core.model.motif.Asset
import com.ssafy.motif.core.model.motif.AssetAvailability

internal object AssetListMockData {

    // TODO 에셋 전용 이미지로 교체. 현재는 designsystem 리소스를 빌려 씀
    private const val THUMBNAIL_URL =
        "android.resource://com.ssafy.motif/drawable/img_test_motif"
    private const val PROFILE_URL =
        "android.resource://com.ssafy.motif/drawable/img_profile"

    val karina = Artist(
        id = 1L,
        name = "카리나",
        profileImageUrl = PROFILE_URL,
        groupName = "aespa",
    )

    val jisoo = Artist(
        id = 2L,
        name = "지수",
        profileImageUrl = PROFILE_URL,
        groupName = "BLACKPINK",
    )

    val chaeyoung = Artist(
        id = 3L,
        name = "이채영",
        profileImageUrl = PROFILE_URL,
        groupName = "fromis_9",
    )

    val haewon = Artist(
        id = 4L,
        name = "오해원",
        profileImageUrl = PROFILE_URL,
        groupName = "NMIXX",
    )

    val yeji = Artist(
        id = 5L,
        name = "YEJI",
        profileImageUrl = PROFILE_URL,
        groupName = "ITZY",
    )

    // 사용 가능한 에셋이 하나도 없는 아티스트. 필터 목록에 나오지 않아야 한다.
    val winter = Artist(
        id = 6L,
        name = "윈터",
        profileImageUrl = PROFILE_URL,
        groupName = "aespa",
    )

    private val titles = listOf(
        "카리나와 함께 만드는 핑거하트 모먼트 💖",
        "카페 맞은편 자리에 앉아 예지와 함께 여유로운 순간을 ☕",
        "지수와 함께하는 봄날의 피크닉 🌸",
        "무대 뒤에서 이채영과 나누는 비하인드 토크 🎤",
        "오해원과 함께 걷는 한강 노을 산책 🌇",
        "연습실에서 카리나와 함께 마지막 동선 체크까지 완벽하게 맞춰보는 리허설 타임 🕺",
        "지수의 플레이리스트로 시작하는 아침 ☀️",
        "예지와 함께 찍는 즉석 사진 📸",
    )

    private val availabilities = listOf(
        AssetAvailability.PURCHASED,
        AssetAvailability.SUBSCRIPTION,
        AssetAvailability.SUBSCRIPTION,
        AssetAvailability.LOCKED,
    )

    // 사용 가능 / 불가 에셋이 섞여 있다. 화면에는 isAvailable == true 인 것만 나와야 한다.
    val assets: List<Asset> = buildList {
        val artists = listOf(karina, yeji, jisoo, chaeyoung, haewon)

        repeat(30) { index ->
            add(
                Asset(
                    id = index + 1L,
                    thumbnailUrl = THUMBNAIL_URL,
                    title = titles[index % titles.size],
                    artist = artists[index % artists.size],
                    availability = availabilities[index % availabilities.size],
                ),
            )
        }

        add(
            Asset(
                id = 31L,
                thumbnailUrl = THUMBNAIL_URL,
                title = "윈터와 함께하는 겨울 산책 ❄️",
                artist = winter,
                availability = AssetAvailability.LOCKED,
            ),
        )
    }
}
