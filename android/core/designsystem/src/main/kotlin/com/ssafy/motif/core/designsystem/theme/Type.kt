package com.ssafy.motif.core.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.ssafy.motif.core.designsystem.R

@OptIn(ExperimentalTextApi::class)
private fun pretendardFont(
    weight: FontWeight,
): Font = Font(
    resId = R.font.pretendard_variable,
    weight = weight,
    style = FontStyle.Normal,
    variationSettings = FontVariation.Settings(
        FontVariation.weight(weight.weight),
    ),
)

private val paytoneOneFontFamily = FontFamily(
    Font(
        resId = R.font.paytone_one_regular,
        weight = FontWeight.Normal,
        style = FontStyle.Normal,
    ),
)

internal val pretendardFontFamily = FontFamily(
    pretendardFont(FontWeight.Normal),
    pretendardFont(FontWeight.Medium),
    pretendardFont(FontWeight.SemiBold),
    pretendardFont(FontWeight.Bold),
)

@Immutable
data class MotifTypography(
    val rankR36: TextStyle,
    val titleB22: TextStyle,
    val titleB20: TextStyle,
    val titleSB20: TextStyle,
    val titleB18: TextStyle,
    val titleM18: TextStyle,
    val titleSB18: TextStyle,

    val bodyR16: TextStyle,
    val bodySB16: TextStyle,
    val bodyR14: TextStyle,
    val bodySB14: TextStyle,

    val captionR12: TextStyle,
    val captionM12: TextStyle,
    val captionSB12: TextStyle,
    val captionR10: TextStyle,
    val captionSB10: TextStyle,
    val captionSB8: TextStyle,
    val captionR8: TextStyle,
) {
    companion object {
        /**
         * Provides the default typography styles for the app.
         */
        @Composable
        fun defaultTypography(): MotifTypography = MotifTypography(
            rankR36 = TextStyle(
                fontFamily = paytoneOneFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 36.sp,
                lineHeight = 52.sp,
            ),

            titleB22 = TextStyle(
                fontFamily = pretendardFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                lineHeight = 26.sp,
            ),
            titleB20 = TextStyle(
                fontFamily = pretendardFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                lineHeight = 24.sp,
            ),
            titleSB20 = TextStyle(
                fontFamily = pretendardFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 20.sp,
                lineHeight = 24.sp,
            ),
            titleB18 = TextStyle(
                fontFamily = pretendardFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                lineHeight = 22.sp,
            ),
            titleSB18 = TextStyle(
                fontFamily = pretendardFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
                lineHeight = 22.sp,
            ),
            titleM18 = TextStyle(
                fontFamily = pretendardFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 18.sp,
                lineHeight = 22.sp,
            ),
            bodyR16 = TextStyle(
                fontFamily = pretendardFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp,
                lineHeight = 20.sp,
            ),
            bodySB16 = TextStyle(
                fontFamily = pretendardFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                lineHeight = 20.sp,
            ),
            bodyR14 = TextStyle(
                fontFamily = pretendardFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
                lineHeight = 18.sp,
            ),
            bodySB14 = TextStyle(
                fontFamily = pretendardFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                lineHeight = 18.sp,
            ),
            captionSB12 = TextStyle(
                fontFamily = pretendardFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                lineHeight = 16.sp,
            ),
            captionM12 = TextStyle(
                fontFamily = pretendardFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                lineHeight = 16.sp,
            ),
            captionR12 = TextStyle(
                fontFamily = pretendardFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 12.sp,
                lineHeight = 16.sp,
            ),

            captionSB10 = TextStyle(
                fontFamily = pretendardFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 10.sp,
                lineHeight = 14.sp,
            ),
            captionR10 = TextStyle(
                fontFamily = pretendardFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 10.sp,
                lineHeight = 14.sp,
            ),
            captionSB8 = TextStyle(
                fontFamily = pretendardFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 8.sp,
                lineHeight = 12.sp,
            ),
            captionR8 = TextStyle(
                fontFamily = pretendardFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 8.sp,
                lineHeight = 12.sp,
            ),

        )
    }
}
