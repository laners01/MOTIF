package com.ssafy.motif.feature.motif.impl.shop.detail

import com.ssafy.motif.core.model.shop.ArtistPass
import com.ssafy.motif.core.model.shop.PassPlan
import com.ssafy.motif.core.model.shop.ShopArtistDetail
import com.ssafy.motif.feature.motif.impl.shop.ShopMockData

/**
 * 샵 목록 Mock([ShopMockData])의 섹션을 상세로 확장한다.
 * 목록과 같은 에셋 id·구매 상태를 쓰므로 목록에서 진입한 상세가 어긋나지 않는다.
 */
internal object ProductDetailMockData {

    private const val ASSET_PRICE = 3_900

    private val passPlans = listOf(
        PassPlan(id = 1L, months = 1, monthlyPrice = 9_900),
        PassPlan(id = 2L, months = 6, monthlyPrice = 8_200),
        PassPlan(id = 3L, months = 12, monthlyPrice = 6_000, note = "+ 시크릿 에셋"),
    )

    // 실기기에서 실제 재생을 확인할 수 있는 16:9 샘플 영상
    private val previewVideoUrls = listOf(
        "https://res.cloudinary.com/demo/video/upload/w_960,h_540,c_fill/kitten_fighting.mp4",
        "https://res.cloudinary.com/demo/video/upload/w_960,h_540,c_fill/dog.mp4",
        "https://res.cloudinary.com/demo/video/upload/w_960,h_540,c_fill/pencil_sketch.mp4",
        "https://res.cloudinary.com/demo/video/upload/w_960,h_540,c_fill/turtle.mp4",
        "https://res.cloudinary.com/demo/video/upload/w_960,h_540,c_fill/elephants.mp4",
    )

    private const val PASS_HEADLINE = "좋아하는 아티스트의 MOTIF를 더 자유롭게 이용해보세요."
    private const val PASS_DESCRIPTION =
        "아티스트 PASS를 구독하면 구독 기간 동안 해당 아티스트의 일반 에셋을 개별 구매 없이 이용할 수 있습니다. " +
            "새로운 일반 에셋이 추가되면 별도 결제 없이 바로 사용할 수 있습니다."

    fun detailOf(artistId: Long): ShopArtistDetail? {
        val section = ShopMockData.sections.firstOrNull { it.artist.id == artistId } ?: return null
        val artist = section.artist

        val bannerImageUrl = ShopMockData.banners
            .firstOrNull { it.artist.id == artistId }
            ?.imageUrl
            ?: ShopMockData.PASS_BANNER_URL

        return ShopArtistDetail(
            artist = artist,
            introduction = "선물처럼 찾아온 ${artist.name}와 함께하는 설레는 일상.\n" +
                "${artist.name} 아티스트와 만드는 나만의 특별한 순간, MOTIF",
            assets = section.assets.mapIndexed { index, asset ->
                asset.copy(
                    price = ASSET_PRICE,
                    previewVideoUrl = previewVideoUrls[index % previewVideoUrls.size],
                )
            },
            pass = ArtistPass(
                artistId = artistId,
                bannerImageUrl = bannerImageUrl,
                headline = PASS_HEADLINE,
                description = PASS_DESCRIPTION,
                plans = passPlans,
            ),
            hasPass = section.hasPass,
        )
    }
}
