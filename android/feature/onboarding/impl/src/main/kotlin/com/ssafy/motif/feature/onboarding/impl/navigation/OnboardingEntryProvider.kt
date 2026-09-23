package com.ssafy.motif.feature.onboarding.impl.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.ssafy.motif.feature.onboarding.api.navigation.OnboardingNavKey
import com.ssafy.motif.feature.onboarding.impl.onboarding.OnboardingScreen

fun EntryProviderScope<NavKey>.onboardingEntry(
    onCompleteClick: () -> Unit,
) {
    entry<OnboardingNavKey> {
        OnboardingScreen(
            onCompleteClick =
                onCompleteClick,
        )
    }
}