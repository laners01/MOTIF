package com.ssafy.motif.feature.signin.impl.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.ssafy.motif.feature.signin.api.navigation.SignInNavKey
import com.ssafy.motif.feature.signin.impl.SignInScreen

fun EntryProviderScope<NavKey>.signInEntry(
    isDemoMode: Boolean = false,
    onDemoSignInComplete: () -> Unit = {},
) {
    entry<SignInNavKey> {
        SignInScreen(
            isDemoMode =
                isDemoMode,
            onDemoSignInComplete =
                onDemoSignInComplete,
        )
    }
}