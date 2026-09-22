package com.ssafy.motif.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import coil.compose.AsyncImage
import com.ssafy.motif.R
import com.ssafy.motif.core.designsystem.component.GlassNavigationBar
import com.ssafy.motif.core.designsystem.component.GlassNavigationBarBackButton
import com.ssafy.motif.core.designsystem.component.GlassNavigationBarItem
import com.ssafy.motif.core.designsystem.component.Icon
import com.ssafy.motif.core.designsystem.glass.GlassBackdropState
import com.ssafy.motif.core.designsystem.icon.MotifIcons
import com.ssafy.motif.core.designsystem.theme.MotifTheme
import com.ssafy.motif.feature.home.api.navigation.HomeNavKey
import com.ssafy.motif.feature.motif.api.navigation.MotifSubMenu
import com.ssafy.motif.feature.mypage.api.navigation.MyPageNavKey

internal enum class AppGlassNavigationBarMode {
    MAIN,
    MOTIF,
}

@Composable
internal fun AppGlassNavigationBar(
    mode: AppGlassNavigationBarMode,
    backdropState: GlassBackdropState,
    currentTopLevelKey: NavKey,
    selectedMotifSubMenu: MotifSubMenu,
    myProfileImageUrl: String,
    onHomeClick: () -> Unit,
    onChallengeClick: () -> Unit,
    onMyClick: () -> Unit,
    onMotifClick: () -> Unit,
    onMotifBackClick: () -> Unit,
    onMotifSubMenuClick: (MotifSubMenu) -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassNavigationBar(
        backdropState = backdropState,
        modifier = modifier,
    ) {
        AnimatedContent(
            targetState = mode,
            transitionSpec = {
                navigationBarTransition(
                    initialMode = initialState,
                    targetMode = targetState,
                )
            },
            modifier = Modifier.weight(1f),
            label = "AppGlassNavigationBarTransition",
        ) { targetMode ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                when (targetMode) {
                    AppGlassNavigationBarMode.MAIN -> {
                        MainNavigationContent(
                            currentTopLevelKey = currentTopLevelKey,
                            myProfileImageUrl = myProfileImageUrl,
                            onHomeClick = onHomeClick,
                            onChallengeClick = onChallengeClick,
                            onMyClick = onMyClick,
                            onMotifClick = onMotifClick,
                        )
                    }

                    AppGlassNavigationBarMode.MOTIF -> {
                        MotifNavigationContent(
                            selectedMotifSubMenu = selectedMotifSubMenu,
                            onBackClick = onMotifBackClick,
                            onSubMenuClick = onMotifSubMenuClick,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.MainNavigationContent(
    currentTopLevelKey: NavKey,
    myProfileImageUrl: String,
    onHomeClick: () -> Unit,
    onChallengeClick: () -> Unit,
    onMyClick: () -> Unit,
    onMotifClick: () -> Unit,
) {
    GlassNavigationBarItem(
        selected = currentTopLevelKey == HomeNavKey,
        onClick = onHomeClick,
        icon = painterResource(
            MotifIcons.Home,
        ),
        label = stringResource(
            R.string.navigation_home,
        ),
    )

    GlassNavigationBarItem(
        selected = false,
        onClick = onChallengeClick,
        icon = painterResource(
            MotifIcons.Play,
        ),
        label = stringResource(
            R.string.navigation_challenge,
        ),
    )

    val isMySelected =
        currentTopLevelKey == MyPageNavKey

    GlassNavigationBarItem(
        selected = isMySelected,
        onClick = onMyClick,
        label = stringResource(
            R.string.navigation_my,
        ),
        iconContent = {
            MyNavigationIcon(
                profileImageUrl = myProfileImageUrl,
                selected = isMySelected,
            )
        },
    )

    GlassNavigationBarItem(
        selected = false,
        onClick = onMotifClick,
        label = stringResource(
            R.string.navigation_motif,
        ),
        iconContent = {
            MotifNavigationIcon(
                selected = false,
            )
        },
    )
}

@Composable
private fun RowScope.MotifNavigationContent(
    selectedMotifSubMenu: MotifSubMenu,
    onBackClick: () -> Unit,
    onSubMenuClick: (MotifSubMenu) -> Unit,
) {
    GlassNavigationBarBackButton(
        onClick = onBackClick,
        contentDescription = stringResource(
            R.string.navigation_back,
        ),
    )

    val isMotifSelected =
        selectedMotifSubMenu == MotifSubMenu.ASSETS

    GlassNavigationBarItem(
        selected = isMotifSelected,
        onClick = {
            onSubMenuClick(
                MotifSubMenu.ASSETS,
            )
        },
        label = stringResource(
            R.string.navigation_motif,
        ),
        iconContent = {
            MotifNavigationIcon(
                selected = isMotifSelected,
            )
        },
    )

    GlassNavigationBarItem(
        selected = selectedMotifSubMenu == MotifSubMenu.SHOP,
        onClick = {
            onSubMenuClick(
                MotifSubMenu.SHOP,
            )
        },
        icon = painterResource(
            MotifIcons.Bag,
        ),
        label = stringResource(
            R.string.navigation_shop,
        ),
    )

    GlassNavigationBarItem(
        selected = selectedMotifSubMenu == MotifSubMenu.ARCHIVE,
        onClick = {
            onSubMenuClick(
                MotifSubMenu.ARCHIVE,
            )
        },
        icon = painterResource(
            MotifIcons.MediaStrip,
        ),
        label = stringResource(
            R.string.navigation_archive,
        ),
    )
}

private fun navigationBarTransition(
    initialMode: AppGlassNavigationBarMode,
    targetMode: AppGlassNavigationBarMode,
): ContentTransform {
    val isForward =
        initialMode == AppGlassNavigationBarMode.MAIN &&
                targetMode == AppGlassNavigationBarMode.MOTIF

    return if (isForward) {
        (
                slideInHorizontally(
                    animationSpec = tween(
                        durationMillis = NavigationTransitionDurationMillis,
                    ),
                    initialOffsetX = { width ->
                        width
                    },
                ) + fadeIn(
                    animationSpec = tween(
                        durationMillis = NavigationTransitionDurationMillis,
                    ),
                )
                ).togetherWith(
                slideOutHorizontally(
                    animationSpec = tween(
                        durationMillis = NavigationTransitionDurationMillis,
                    ),
                    targetOffsetX = { width ->
                        -width
                    },
                ) + fadeOut(
                    animationSpec = tween(
                        durationMillis = NavigationTransitionDurationMillis,
                    ),
                ),
            )
    } else {
        (
                slideInHorizontally(
                    animationSpec = tween(
                        durationMillis = NavigationTransitionDurationMillis,
                    ),
                    initialOffsetX = { width ->
                        -width
                    },
                ) + fadeIn(
                    animationSpec = tween(
                        durationMillis = NavigationTransitionDurationMillis,
                    ),
                )
                ).togetherWith(
                slideOutHorizontally(
                    animationSpec = tween(
                        durationMillis = NavigationTransitionDurationMillis,
                    ),
                    targetOffsetX = { width ->
                        width
                    },
                ) + fadeOut(
                    animationSpec = tween(
                        durationMillis = NavigationTransitionDurationMillis,
                    ),
                ),
            )
    }
}

@Composable
private fun MyNavigationIcon(
    profileImageUrl: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    AsyncImage(
        model = profileImageUrl,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .fillMaxSize()
            .clip(CircleShape)
            .border(
                width = NavigationIconBorderWidth,
                color = navigationIconBorderColor(
                    selected = selected,
                ),
                shape = CircleShape,
            ),
    )
}

@Composable
private fun MotifNavigationIcon(
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    Icon(
        painter = painterResource(
            MotifIcons.Motif,
        ),
        contentDescription = null,
        tint = Color.Unspecified,
        modifier = modifier
            .fillMaxSize()
            .border(
                width = NavigationIconBorderWidth,
                color = navigationIconBorderColor(
                    selected = selected,
                ),
                shape = RoundedCornerShape(
                    NavigationMotifCornerRadius,
                ),
            ),
    )
}

@Composable
private fun navigationIconBorderColor(
    selected: Boolean,
): Color =
    if (selected) {
        MotifTheme.colors.white
    } else {
        MotifTheme.colors.gray500
    }

private const val NavigationTransitionDurationMillis = 300

private val NavigationIconBorderWidth = 1.dp

private val NavigationMotifCornerRadius = 4.dp