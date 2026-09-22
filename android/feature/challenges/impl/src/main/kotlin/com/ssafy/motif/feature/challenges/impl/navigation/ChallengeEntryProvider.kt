package com.ssafy.motif.feature.challenges.impl.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.ssafy.motif.feature.challenges.api.navigation.ChallengeNavKey
import com.ssafy.motif.feature.challenges.impl.artist.ArtistChallengeScreen

fun EntryProviderScope<NavKey>.challengeEntry(
    myProfileImageUrl: String,
    onHomeClick: () -> Unit,
    onMyClick: () -> Unit,
    onMotifClick: () -> Unit,
    onArtistClick: (Long) -> Unit,
    onCaptureClick: (Long) -> Unit,
) {
    entry<ChallengeNavKey> {
        ArtistChallengeScreen(
            myProfileImageUrl = myProfileImageUrl,
            onArtistClick = onArtistClick,
            onCaptureClick = onCaptureClick,
            onHomeClick = onHomeClick,
            onMyClick = onMyClick,
            onMotifClick = onMotifClick,
        )
    }
}