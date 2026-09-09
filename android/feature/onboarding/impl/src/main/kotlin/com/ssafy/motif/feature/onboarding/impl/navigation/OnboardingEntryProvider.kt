package com.ssafy.motif.feature.onboarding.impl.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.ssafy.motif.core.navigation.Navigator
import com.ssafy.motif.feature.onboarding.api.navigation.ArtistSelectNavKey
import com.ssafy.motif.feature.onboarding.api.navigation.NicknameInputNavKey
import com.ssafy.motif.feature.onboarding.impl.artistselect.ArtistSelectRoute
import com.ssafy.motif.feature.onboarding.impl.nicknameinput.NicknameInputRoute

fun EntryProviderScope<NavKey>.onboardingEntry(navigator: Navigator) {
    entry <ArtistSelectNavKey> {
        ArtistSelectRoute(
            onNextClick = {ids ->navigator.navigate(NicknameInputNavKey(ids))}
        )
    }
    entry<NicknameInputNavKey> { key ->
        NicknameInputRoute(
            artistIds = key.artistIds,
            onCompleteClick = {},
        )
    }
}