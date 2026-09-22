package com.ssafy.motif.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.ssafy.motif.core.designsystem.component.GlassNavigationBarDefaults
import com.ssafy.motif.core.designsystem.glass.glassBackdrop
import com.ssafy.motif.core.designsystem.glass.rememberGlassBackdropState
import com.ssafy.motif.core.navigation.Navigator
import com.ssafy.motif.core.navigation.toEntries
import com.ssafy.motif.feature.artistdetail.impl.navigation.artistDetailEntry
import com.ssafy.motif.feature.capture.api.navigation.CaptureNavKey
import com.ssafy.motif.feature.capture.impl.navigation.captureEntry
import com.ssafy.motif.feature.challenges.api.navigation.ChallengeNavKey
import com.ssafy.motif.feature.challenges.impl.navigation.challengeEntry
import com.ssafy.motif.feature.home.api.navigation.HomeNavKey
import com.ssafy.motif.feature.home.impl.navigation.homeEntry
import com.ssafy.motif.feature.motif.api.navigation.MotifNavKey
import com.ssafy.motif.feature.motif.impl.navigation.motifEntry
import com.ssafy.motif.feature.mypage.api.navigation.MyPageNavKey
import com.ssafy.motif.feature.mypage.impl.navigation.myPageEntry
import com.ssafy.motif.feature.onboarding.impl.navigation.onboardingEntry
import com.ssafy.motif.feature.ranking.impl.navigation.rankingEntry
import com.ssafy.motif.navigation.AppGlassNavigationBar
import com.ssafy.motif.navigation.AppGlassNavigationBarMode

@Composable
fun MotifApp(
    appState: MotifAppState,
    modifier: Modifier = Modifier,
    myProfileImageUrl: String = "",
) {
    val navigator = remember(
        appState.navigationState,
    ) {
        Navigator(
            appState.navigationState,
        )
    }

    val backdropState = rememberGlassBackdropState()

    val entryProvider = entryProvider {
        homeEntry(
            navigator = navigator,
        )

        challengeEntry(
            myProfileImageUrl =
                myProfileImageUrl,
            onHomeClick = {
                navigator.navigate(
                    HomeNavKey,
                )
            },
            onMyClick = {
                navigator.navigate(
                    MyPageNavKey,
                )
            },
            onMotifClick = {
                navigator.navigate(
                    MotifNavKey,
                )
            },
            onArtistClick = { artistId ->
                // 기존 ArtistDetail 연결
            },
            onCaptureClick = {
                    challengeId,
                    challengeThumbnailUrl,
                ->

                navigator.navigate(
                    CaptureNavKey(
                        challengeId =
                            challengeId,
                        challengeThumbnailUrl =
                            challengeThumbnailUrl,
                    ),
                )
            },
        )

        onboardingEntry(
            navigator = navigator,
        )

        rankingEntry(
            navigator = navigator,
        )

        captureEntry(
            onBackClick =
                navigator::goBack,
            onBrowseChallengeClick = {
                navigator.navigate(
                    ChallengeNavKey,
                )
            },
            onError = { throwable ->
                // TODO 공통 Snackbar/Error 처리 연결
            },
        )

        artistDetailEntry(
            navigator = navigator,
        )

        myPageEntry(
            navigator = navigator,
        )

        motifEntry(
            navigator = navigator,
            selectedMenu = {
                appState.selectedMotifSubMenu
            },
        )
    }

    val currentTopLevelKey =
        appState.navigationState.currentTopLevelKey

    val currentKey =
        appState.navigationState.currentKey

    val navigationBarMode =
        when {
            currentTopLevelKey == MotifNavKey &&
                    currentKey == MotifNavKey -> {
                AppGlassNavigationBarMode.MOTIF
            }

            currentTopLevelKey == HomeNavKey ||
                    currentTopLevelKey == MyPageNavKey -> {
                AppGlassNavigationBarMode.MAIN
            }

            else -> {
                null
            }
        }

    Box(
        modifier = modifier.fillMaxSize(),
    ) {
        NavDisplay(
            entries = appState.navigationState.toEntries(
                entryProvider = entryProvider,
            ),
            onBack = navigator::goBack,
            modifier = Modifier
                .fillMaxSize()
                .glassBackdrop(
                    backdropState,
                )
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Top,
                    ),
                ),
        )

        navigationBarMode?.let { mode ->
            AppGlassNavigationBar(
                mode = mode,
                backdropState = backdropState,
                currentTopLevelKey = currentTopLevelKey,
                selectedMotifSubMenu = appState.selectedMotifSubMenu,
                myProfileImageUrl = myProfileImageUrl,
                onHomeClick = {
                    navigator.navigate(
                        HomeNavKey,
                    )
                },
                onChallengeClick = { navigator.navigate(ChallengeNavKey) },
                onMyClick = {
                    navigator.navigate(
                        MyPageNavKey,
                    )
                },
                onMotifClick = {
                    navigator.navigate(
                        MotifNavKey,
                    )
                },
                onMotifBackClick = {
                    navigator.goBack()
                },
                onMotifSubMenuClick = {
                    appState.selectMotifSubMenu(
                        it,
                    )
                },
                modifier = Modifier
                    .align(
                        Alignment.BottomCenter,
                    )
                    .navigationBarsPadding()
                    .padding(
                        start = GlassNavigationBarDefaults.HorizontalMargin,
                        end = GlassNavigationBarDefaults.HorizontalMargin,
                        bottom = GlassNavigationBarDefaults.BottomMargin,
                    ),
            )
        }
    }
}