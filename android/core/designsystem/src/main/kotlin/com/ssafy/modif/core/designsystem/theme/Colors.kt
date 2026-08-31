package com.ssafy.modif.core.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import com.ssafy.modif.core.designsystem.R

@Immutable
data class ModifColors(
    val white: Color,
    val gray50: Color,
    val gray100: Color,
    val gray200: Color,
    val gray300: Color,
    val gray400: Color,
    val gray500: Color,
    val gray700: Color,
    val gray900: Color,
    val black: Color,
) {
    companion object {
        /**
         * Provides the default colors for the light mode of the app.
         */
        @Composable
        fun defaultColors(): ModifColors = ModifColors(
            white = colorResource(R.color.white),
            gray50 = colorResource(R.color.gray_50),
            gray100 = colorResource(R.color.gray_100),
            gray200 = colorResource(R.color.gray_200),
            gray300 = colorResource(R.color.gray_300),
            gray400 = colorResource(R.color.gray_400),
            gray500 = colorResource(R.color.gray_500),
            gray700 = colorResource(R.color.gray_700),
            gray900 = colorResource(R.color.gray_900),
            black = colorResource(R.color.black),
        )
    }
}
