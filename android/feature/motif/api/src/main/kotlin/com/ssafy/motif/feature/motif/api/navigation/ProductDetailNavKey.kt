package com.ssafy.motif.feature.motif.api.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * 아티스트별 모티프 상품 상세.
 *
 * @param artistId 상세를 볼 아티스트.
 * @param assetId 진입 시 선택되어 있을 에셋. 목록에 없으면 첫 에셋으로 대체된다.
 */
@Serializable
data class ProductDetailNavKey(
    val artistId: Long,
    val assetId: Long,
) : NavKey
