package com.ssafy.motif.feature.capture.impl.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.ssafy.motif.feature.capture.api.navigation.CaptureNavKey
import com.ssafy.motif.feature.capture.impl.challenge.CaptureScreen

fun EntryProviderScope<NavKey>.captureEntry(
    onBackClick: () -> Unit,
    onBrowseChallengeClick: () -> Unit,
    onError: (Throwable) -> Unit,
) {
    entry<CaptureNavKey> { key ->
        CaptureScreen(
            challengeId =
                key.challengeId,
            challengeThumbnailUrl =
                key.challengeThumbnailUrl,
            onBackClick =
                onBackClick,
            onBrowseChallengeClick =
                onBrowseChallengeClick,
            onError =
                onError,
        )
    }
}