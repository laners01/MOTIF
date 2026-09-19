package com.ssafy.motif.feature.motif.impl.shop

import com.ssafy.motif.core.model.home.Artist
import com.ssafy.motif.core.model.motif.Asset
import com.ssafy.motif.core.model.motif.AssetAvailability
import com.ssafy.motif.core.model.shop.PassBanner
import com.ssafy.motif.core.model.shop.ShopArtistSection

internal object ShopMockData {

    // TODO 샵 전용 이미지로 교체. 배너는 디자인에서 이미지로 제공될 예정이라 임시로 썸네일을 쓴다
    private const val THUMBNAIL_URL =
        "android.resource://com.ssafy.motif/drawable/img_test_motif"
    private const val PROFILE_URL =
        "android.resource://com.ssafy.motif/drawable/img_profile"

    private val yeji = Artist(id = 1L, name = "Yeji", profileImageUrl = PROFILE_URL, groupName = "ITZY")
    private val karina = Artist(id = 2L, name = "Karina", profileImageUrl = PROFILE_URL, groupName = "aespa")
    private val jisoo = Artist(id = 3L, name = "Jisoo", profileImageUrl = PROFILE_URL, groupName = "BLACKPINK")
    private val chaeyoung = Artist(id = 4L, name = "Chaeyoung", profileImageUrl = PROFILE_URL, groupName = "fromis_9")
    private val haewon = Artist(id = 5L, name = "Haewon", profileImageUrl = PROFILE_URL, groupName = "NMIXX")
    private val winter = Artist(id = 6L, name = "Winter", profileImageUrl = PROFILE_URL, groupName = "aespa")
    private val minji = Artist(id = 7L, name = "Minji", profileImageUrl = PROFILE_URL, groupName = "NewJeans")

    val banners: List<PassBanner> = listOf(
        PassBanner(id = 1L, artist = yeji, imageUrl = THUMBNAIL_URL),
        PassBanner(id = 2L, artist = karina, imageUrl = THUMBNAIL_URL),
        PassBanner(id = 3L, artist = jisoo, imageUrl = THUMBNAIL_URL),
    )

    private val titles = listOf(
        "%s와 함께 만드는 핑거하트 모먼트 💖",
        "카페 맞은편 자리에 앉아 %s와 함께 여유로운 순간을 ☕",
        "%s와 함께하는 봄날의 피크닉 🌸",
        "무대 뒤에서 %s와 나누는 비하인드 토크 🎤",
        "%s와 함께 걷는 한강 노을 산책 🌇",
        "연습실에서 %s와 함께 마지막 동선 체크까지 완벽하게 맞춰보는 리허설 타임 🕺",
    )

    // 7개 섹션 → 페이지 크기 3이면 3 / 3 / 1 로 나뉘어 hasNext 경계를 확인할 수 있다.
    val sections: List<ShopArtistSection> = listOf(
        section(artist = yeji, hasPass = true, assetCount = 5, purchasedIndices = emptySet(), premiumIndices = setOf(0)),
        section(artist = karina, hasPass = false, assetCount = 6, purchasedIndices = setOf(0), premiumIndices = setOf(2)),
        section(artist = jisoo, hasPass = false, assetCount = 4, purchasedIndices = setOf(1, 3), premiumIndices = emptySet()),
        section(artist = chaeyoung, hasPass = true, assetCount = 5, purchasedIndices = emptySet(), premiumIndices = setOf(4)),
        section(artist = haewon, hasPass = false, assetCount = 6, purchasedIndices = emptySet(), premiumIndices = setOf(0, 1)),
        section(artist = winter, hasPass = false, assetCount = 4, purchasedIndices = setOf(2), premiumIndices = emptySet()),
        section(artist = minji, hasPass = false, assetCount = 5, purchasedIndices = emptySet(), premiumIndices = setOf(3)),
    )

    private fun section(
        artist: Artist,
        hasPass: Boolean,
        assetCount: Int,
        purchasedIndices: Set<Int>,
        premiumIndices: Set<Int>,
    ): ShopArtistSection {
        val assets = List(assetCount) { index ->
            Asset(
                // 아티스트별로 id 구간을 나눠 전체 샵에서 유일하게 만든다.
                id = artist.id * 100 + index + 1,
                thumbnailUrl = THUMBNAIL_URL,
                title = titles[index % titles.size].format(artist.name),
                artist = artist,
                availability = when {
                    index in purchasedIndices -> AssetAvailability.PURCHASED
                    hasPass -> AssetAvailability.SUBSCRIPTION
                    else -> AssetAvailability.LOCKED
                },
                isPremium = index in premiumIndices,
            )
        }

        return ShopArtistSection(
            artist = artist,
            hasPass = hasPass,
            assets = assets,
        )
    }
}
