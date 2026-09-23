package com.ssafy.motif.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.ssafy.motif.core.designsystem.component.GlassNavigationBarDefaults
import com.ssafy.motif.core.designsystem.glass.glassBackdrop
import com.ssafy.motif.core.designsystem.glass.rememberGlassBackdropState
import com.ssafy.motif.core.model.user.UserActivityTargetType
import com.ssafy.motif.core.navigation.Navigator
import com.ssafy.motif.core.navigation.toEntries
import com.ssafy.motif.feature.artistdetail.impl.navigation.artistDetailEntry
import com.ssafy.motif.feature.capture.api.navigation.CaptureNavKey
import com.ssafy.motif.feature.capture.impl.navigation.captureEntry
import com.ssafy.motif.feature.challenges.api.navigation.ArtistChallengeNavKey
import com.ssafy.motif.feature.challenges.api.navigation.ChallengeNavKey
import com.ssafy.motif.feature.challenges.api.navigation.ChallengeParticipationNavKey
import com.ssafy.motif.feature.challenges.impl.navigation.challengeEntry
import com.ssafy.motif.feature.home.api.navigation.HomeNavKey
import com.ssafy.motif.feature.home.impl.navigation.homeEntry
import com.ssafy.motif.feature.motif.api.navigation.MotifNavKey
import com.ssafy.motif.feature.motif.impl.navigation.motifEntry
import com.ssafy.motif.feature.mypage.api.navigation.MyPageNavKey
import com.ssafy.motif.feature.mypage.impl.navigation.myPageEntry
import com.ssafy.motif.feature.onboarding.impl.navigation.onboardingEntry
import com.ssafy.motif.feature.ranking.impl.navigation.rankingEntry
import com.ssafy.motif.feature.signin.impl.navigation.signInEntry
import com.ssafy.motif.navigation.AppGlassNavigationBar
import com.ssafy.motif.navigation.AppGlassNavigationBarMode

@Composable
fun MotifApp(
    appState: MotifAppState,
    modifier: Modifier = Modifier,
    myProfileImageUrl: String = "https://i.namu.wiki/i/UrOyzR3UM58sJpKFwGILzDAVo6pOAqT8fY8wSMszt2Z78mRcTG3znpyTcqsRAy3c4AvBSFfhKUTdcS1m4KW0lA.webp",
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
        signInEntry()
        homeEntry(
            onOnstageClick = { videoId ->
                navigator.navigate(
                    ChallengeParticipationNavKey(
                        initialVideoId =
                            videoId,
                    ),
                )
            },
            onFanRadarClick = {
                // TODO 아티스트 랭킹 화면으로 이동
            },
            onArtistClick = { artistId ->
                // TODO 아티스트 상세 화면으로 이동
            },
            onMotifClick = { motifId ->
                // TODO 모티프 상세 화면으로 이동
            },
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
            onParticipationClick = {
                    challengeId,
                ->
                navigator.navigate(
                    ChallengeParticipationNavKey(
                        challengeId =
                            challengeId,
                    ),
                )
            },
            onBackClick =
                navigator::goBack,
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
            onCompleteClick =
                appState::completeOnboarding,
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
            onChallengeClick = {
                    videoId,
                ->
                navigator.navigate(
                    ChallengeParticipationNavKey(
                        initialVideoId =
                            videoId,
                    ),
                )
            },
            onLikedChallengeClick = {
                    targetType,
                    targetId,
                ->
                when (targetType) {
                    UserActivityTargetType.VIDEO -> {
                        navigator.navigate(
                            ChallengeParticipationNavKey(
                                initialVideoId =
                                    targetId,
                            ),
                        )
                    }

                    UserActivityTargetType.CHALLENGE -> {
                        navigator.navigate(
                            ArtistChallengeNavKey(
                                initialChallengeId =
                                    targetId,
                            ),
                        )
                    }
                }
            },
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
            currentKey is
                    ChallengeParticipationNavKey -> {
                null
            }

            currentTopLevelKey ==
                    MotifNavKey &&
                    currentKey ==
                    MotifNavKey -> {
                AppGlassNavigationBarMode.MOTIF
            }

            currentTopLevelKey ==
                    HomeNavKey ||
                    currentTopLevelKey ==
                    MyPageNavKey -> {
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
            transitionSpec = {
                motifPushTransition()
            },
            popTransitionSpec = {
                motifPopTransition()
            },
            predictivePopTransitionSpec = {
                motifPredictivePopTransition()
            },
            onBack = navigator::goBack,
            modifier = Modifier
                .fillMaxSize()
                .glassBackdrop(
                    backdropState,
                )
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