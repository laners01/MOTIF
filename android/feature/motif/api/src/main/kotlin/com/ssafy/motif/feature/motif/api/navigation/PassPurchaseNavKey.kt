package com.ssafy.motif.feature.motif.api.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * 아티스트 구독권(PASS) 결제 화면.
 */
@Serializable
data class PassPurchaseNavKey(
    val artistId: Long,
) : NavKey
