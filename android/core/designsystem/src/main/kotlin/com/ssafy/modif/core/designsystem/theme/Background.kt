package com.ssafy.modif.core.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ssafy.modif.core.designsystem.R

@Immutable
data class ModifBackground(
    val color: Color = Color.Unspecified,
    val tonalElevation: Dp = Dp.Unspecified,
) {
    companion object {
        @Composable
        fun defaultBackground(): ModifBackground =
            ModifBackground(
                color = colorResource(id = R.color.black),
                tonalElevation = 0.dp,
            )
    }
}

val LocalBackgroundTheme: ProvidableCompositionLocal<ModifBackground> =
    staticCompositionLocalOf { ModifBackground() }