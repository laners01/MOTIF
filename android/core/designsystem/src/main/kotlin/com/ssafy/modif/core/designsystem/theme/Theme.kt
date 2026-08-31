package com.ssafy.modif.core.designsystem.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId

private val LocalModifColors = compositionLocalOf<ModifColors> {
    error("No colors provided!")
}

private val LocalModifTypography = compositionLocalOf<ModifTypography> {
    error("No ModifTypography provided!")
}

val LocalModifContentColor = compositionLocalOf { Color(0xFF404040) }

@Composable
fun ModifTheme(
    colors: ModifColors = ModifColors.defaultColors(),
    typography: ModifTypography = ModifTypography.defaultTypography(),
    background: ModifBackground = ModifBackground.defaultBackground(),
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalModifColors provides colors,
        LocalModifTypography provides typography,
        LocalBackgroundTheme provides background,
    ) {
        Box(
            modifier = Modifier
                .background(background.color)
                .semantics { testTagsAsResourceId = true },
        ) {
            content()
        }
    }
}

object ModifTheme {
    val colors: ModifColors
        @Composable
        @ReadOnlyComposable
        get() = LocalModifColors.current

    val typography: ModifTypography
        @Composable
        @ReadOnlyComposable
        get() = LocalModifTypography.current
}
