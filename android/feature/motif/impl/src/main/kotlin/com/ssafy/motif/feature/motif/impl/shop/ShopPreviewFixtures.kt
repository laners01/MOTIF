package com.ssafy.motif.feature.motif.impl.shop

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.ssafy.motif.core.model.home.Artist
import com.ssafy.motif.core.model.motif.Asset
import com.ssafy.motif.core.model.motif.AssetAvailability
import com.ssafy.motif.core.model.shop.PassBanner
import com.ssafy.motif.core.model.shop.ShopArtistSection
import com.ssafy.motif.feature.motif.impl.R
import com.ssafy.motif.core.designsystem.R as DesignSystemR

/**
 * 샵 화면 Preview 공용 데이터.
 * Preview 렌더러는 앱 패키지의 리소스를 직접 못 읽으므로 현재 패키지 기준 android.resource URL로 만든다.
 */
internal object ShopPreviewFixtures {

    @Composable
    fun imageUrl(resId: Int): String {
        val context = LocalContext.current
        return "android.resource://${context.packageName}/$resId"
    }

    @Composable
    fun artists(): List<Artist> {
        val profileUrl = imageUrl(DesignSystemR.drawable.img_profile)

        return listOf(
            Artist(id = 1L, name = "Yeji", profileImageUrl = profileUrl, groupName = "ITZY"),
            Artist(id = 2L, name = "Karina", profileImageUrl = profileUrl, groupName = "aespa"),
            Artist(id = 3L, name = "Jisoo", profileImageUrl = profileUrl, groupName = "BLACKPINK"),
        )
    }

    @Composable
    fun banners(): List<PassBanner> {
        val bannerUrl = imageUrl(R.drawable.img_pass_banner)

        return artists().mapIndexed { index, artist ->
            PassBanner(
                id = index + 1L,
                artist = artist,
                imageUrl = bannerUrl,
            )
        }
    }

    @Composable
    fun assets(
        artist: Artist,
        purchasedIndices: Set<Int> = emptySet(),
    ): List<Asset> {
        val thumbnailUrl = imageUrl(DesignSystemR.drawable.img_test_motif)

        val titles = listOf(
            "${artist.name}와 함께 만드는 핑거하트 모먼트 💖",
            "카페 맞은편 자리에 앉아 ${artist.name}와 함께 여유로운 순간을 ☕",
            "${artist.name}와 함께하는 봄날의 피크닉 🌸",
            "무대 뒤에서 ${artist.name}와 나누는 비하인드 토크 🎤",
        )

        return titles.mapIndexed { index, title ->
            Asset(
                id = artist.id * 100 + index + 1,
                thumbnailUrl = thumbnailUrl,
                title = title,
                artist = artist,
                availability = if (index in purchasedIndices) {
                    AssetAvailability.PURCHASED
                } else {
                    AssetAvailability.LOCKED
                },
            )
        }
    }

    @Composable
    fun sections(): List<ShopArtistSection> {
        val artists = artists()

        return listOf(
            ShopArtistSection(
                artist = artists[0],
                hasPass = true,
                assets = assets(artists[0]),
            ),
            ShopArtistSection(
                artist = artists[1],
                hasPass = false,
                assets = assets(artists[1], purchasedIndices = setOf(0)),
            ),
            ShopArtistSection(
                artist = artists[2],
                hasPass = false,
                assets = assets(artists[2], purchasedIndices = setOf(1, 3)),
            ),
        )
    }
}
