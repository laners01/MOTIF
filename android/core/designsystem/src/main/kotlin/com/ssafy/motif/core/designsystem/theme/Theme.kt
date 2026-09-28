package com.ssafy.motif.core.designsystem.theme

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

private val LocalMotifColors = compositionLocalOf<MotifColors> {
    error("No colors provided!")
}

private val LocalMotifTypography = compositionLocalOf<MotifTypography> {
    error("No MotifTypography provided!")
}

val LocalMotifContentColor = compositionLocalOf { Color(0xFF404040) }

@Composable
fun MotifTheme(
    colors: MotifColors = MotifColors.defaultColors(),
    typography: MotifTypography = MotifTypography.defaultTypography(),
    background: MotifBackground = MotifBackground.defaultBackground(),
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalMotifColors provides colors,
        LocalMotifTypography provides typography,
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

object MotifTheme {
    val colors: MotifColors
        @Composable
        @ReadOnlyComposable
        get() = LocalMotifColors.current

    val typography: MotifTypography
        @Composable
        @ReadOnlyComposable
        get() = LocalMotifTypography.current
}
