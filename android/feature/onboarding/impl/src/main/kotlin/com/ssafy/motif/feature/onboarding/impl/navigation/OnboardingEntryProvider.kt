package com.ssafy.motif.feature.onboarding.impl.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.ssafy.motif.core.navigation.Navigator
import com.ssafy.motif.feature.home.api.navigation.HomeNavKey
import com.ssafy.motif.feature.onboarding.api.navigation.OnboardingNavKey
import com.ssafy.motif.feature.onboarding.impl.onboarding.OnboardingScreen

fun EntryProviderScope<NavKey>.onboardingEntry(navigator: Navigator) {
    entry<OnboardingNavKey> {
        OnboardingScreen(
            onCompleteClick = { navigator.navigate(HomeNavKey) },
        )
    }
}
