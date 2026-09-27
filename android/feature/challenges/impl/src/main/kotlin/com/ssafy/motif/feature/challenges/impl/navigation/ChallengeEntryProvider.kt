package com.ssafy.motif.feature.challenges.impl.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.ssafy.motif.feature.challenges.api.navigation.ArtistChallengeNavKey
import com.ssafy.motif.feature.challenges.api.navigation.ChallengeNavKey
import com.ssafy.motif.feature.challenges.api.navigation.ChallengeParticipationNavKey
import com.ssafy.motif.feature.challenges.impl.artist.ArtistChallengeScreen
import com.ssafy.motif.feature.challenges.impl.participation.ChallengeParticipationScreen

fun EntryProviderScope<NavKey>.challengeEntry(
    myProfileImageUrl: () -> String,
    onHomeClick: () -> Unit,
    onMyClick: () -> Unit,
    onMotifClick: () -> Unit,
    onArtistClick: (Long) -> Unit,
    onParticipationClick: (Long) -> Unit,
    onBackClick: () -> Unit,
    onCaptureClick: (
        challengeId: Long,
        challengeThumbnailUrl: String,
    ) -> Unit,
    onParticipationEditClick:
    ((Long) -> Unit)? = null,
) {
    entry<ChallengeNavKey> {
        ArtistChallengeScreen(
            myProfileImageUrl = myProfileImageUrl(),
            initialChallengeId =
                null,
            onArtistClick =
                onArtistClick,
            onParticipationClick =
                onParticipationClick,
            onCaptureClick =
                onCaptureClick,
            onHomeClick =
                onHomeClick,
            onMyClick =
                onMyClick,
            onMotifClick =
                onMotifClick,
        )
    }

    entry<ArtistChallengeNavKey> { key ->
        ArtistChallengeScreen(
            myProfileImageUrl =
                myProfileImageUrl(),
            initialChallengeId =
                key.initialChallengeId,
            onArtistClick =
                onArtistClick,
            onParticipationClick =
                onParticipationClick,
            onCaptureClick =
                onCaptureClick,
            onHomeClick =
                onHomeClick,
            onMyClick =
                onMyClick,
            onMotifClick =
                onMotifClick,
        )
    }

    entry<ChallengeParticipationNavKey> { key ->
        ChallengeParticipationScreen(
            challengeId =
                key.challengeId,
            initialVideoId =
                key.initialVideoId,
            myProfileImageUrl =
                myProfileImageUrl(),
            onBackClick =
                onBackClick,
            onHomeClick =
                onHomeClick,
            onMyClick =
                onMyClick,
            onMotifClick =
                onMotifClick,
            onEditClick =
                onParticipationEditClick,
        )
    }
}