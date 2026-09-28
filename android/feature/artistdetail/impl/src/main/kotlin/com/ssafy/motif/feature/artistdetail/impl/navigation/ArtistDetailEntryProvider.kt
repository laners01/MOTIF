package com.ssafy.motif.feature.artistdetail.impl.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.ssafy.motif.core.navigation.Navigator
import com.ssafy.motif.feature.artistdetail.api.navigation.ArtistDetailNavKey
import com.ssafy.motif.feature.artistdetail.impl.artistdetail.ArtistDetailScreen
import com.ssafy.motif.feature.challenges.api.navigation.ArtistChallengeNavKey

fun EntryProviderScope<NavKey>.artistDetailEntry(
    navigator: Navigator,
) {
    entry<ArtistDetailNavKey> { key ->
        ArtistDetailScreen(
            artistId =
                key.artistId,
            onBackClick =
                navigator::goBack,
            onChallengeClick = {
                    challengeId,
                ->
                navigator.navigate(
                    ArtistChallengeNavKey(
                        initialChallengeId =
                            challengeId,
                    ),
                )
            },
            onCreateMotifClick = {
                // TODO 모티프 생성 진입 경로 확정 후 연결
            },
        )
    }
}