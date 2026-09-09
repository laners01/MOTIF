package com.ssafy.motif.feature.onboarding.api.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable


@Serializable
data class NicknameInputNavKey(val artistIds:List<Long>): NavKey {
}