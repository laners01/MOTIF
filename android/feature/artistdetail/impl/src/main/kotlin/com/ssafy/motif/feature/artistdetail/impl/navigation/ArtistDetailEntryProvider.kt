package com.ssafy.motif.feature.artistdetail.impl.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.ssafy.motif.core.navigation.Navigator
import com.ssafy.motif.feature.artistdetail.api.navigation.ArtistDetailNavKey
import com.ssafy.motif.feature.artistdetail.impl.artistdetail.ArtistDetailScreen

fun EntryProviderScope<NavKey>.artistDetailEntry(navigator: Navigator) {
    entry<ArtistDetailNavKey> { key ->
        ArtistDetailScreen(artistId = key.artistId)
    }
}
