package com.ssafy.modif.core.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import com.ssafy.modif.core.designsystem.R

@Immutable
data class ModifColors(
    val white: Color,
    val gray300: Color,
    val gray400: Color,
    val gray500: Color,
    val black: Color,
) {
    companion object {
        /**
         * Provides the default colors for the light mode of the app.
         */
        @Composable
        fun defaultColors(): ModifColors = ModifColors(
            white = colorResource(R.color.white),
            gray300 = colorResource(R.color.gray_300),
            gray400 = colorResource(R.color.gray_400),
            gray500 = colorResource(R.color.gray_500),
            black = colorResource(R.color.black),
        )
    }
}
