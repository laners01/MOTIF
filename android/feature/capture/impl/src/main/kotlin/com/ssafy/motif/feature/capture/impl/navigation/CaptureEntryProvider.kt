package com.ssafy.motif.feature.capture.impl.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.ssafy.motif.feature.capture.api.navigation.CaptureNavKey
import com.ssafy.motif.feature.capture.api.navigation.ChallengeCompositeResultNavKey
import com.ssafy.motif.feature.capture.api.navigation.MotifCaptureNavKey
import com.ssafy.motif.feature.capture.impl.challenge.CaptureScreen
import com.ssafy.motif.feature.capture.impl.challenge.result.ChallengeCompositeResultScreen
import com.ssafy.motif.feature.capture.impl.motif.MotifCaptureScreen

fun EntryProviderScope<NavKey>.captureEntry(
    onBackClick: () -> Unit,
    onBrowseChallengeClick: () -> Unit,
    onBrowseMotifClick: (
        artistId: Long,
        assetId: Long,
    ) -> Unit,
    onCompositePublished: (Long) -> Unit,
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

    entry<MotifCaptureNavKey> { key ->
        MotifCaptureScreen(
            assetId = key.assetId,
            onBackClick = onBackClick,
            onBrowseMotifClick =
                onBrowseMotifClick,
            onError = onError,
        )
    }

    entry<ChallengeCompositeResultNavKey> { key ->
        ChallengeCompositeResultScreen(
            jobUuid =
                key.jobUuid,
            onBackClick =
                onBackClick,
            onPublished =
                onCompositePublished,
            onError =
                onError,
        )
    }
}
