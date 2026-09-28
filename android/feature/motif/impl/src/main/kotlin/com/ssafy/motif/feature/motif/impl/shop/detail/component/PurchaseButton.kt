package com.ssafy.motif.feature.motif.impl.shop.detail.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.motif.core.designsystem.component.Button
import com.ssafy.motif.core.designsystem.component.MotifButtonDefaults
import com.ssafy.motif.core.designsystem.component.Text
import com.ssafy.motif.core.designsystem.theme.MotifTheme
import com.ssafy.motif.feature.motif.impl.R

/**
 * 하단 고정 MOTIF 액션 버튼.
 *
 * - 이용 권한 없음: 기존 구매 액션
 * - 개별 구매/PASS로 이용 가능: Moment with {ARTIST} → 촬영 진입
 */
@Composable
internal fun PurchaseButton(
    isAvailable: Boolean,
    artistName: String,
    onPurchaseClick: () -> Unit,
    onMomentClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick =
            if (isAvailable) {
                onMomentClick
            } else {
                onPurchaseClick
            },
        modifier =
            modifier
                .fillMaxWidth()
                .height(ButtonHeight),
        shape =
            RoundedCornerShape(
                ButtonCornerRadius,
            ),
        colors =
            MotifButtonDefaults.buttonColors(
                containerColor =
                    MotifTheme.colors.pink,
                contentColor =
                    MotifTheme.colors.white,
            ),
    ) {
        if (isAvailable) {
            Text(
                text =
                    stringResource(
                        R.string.feature_motif_impl_moment_with,
                        artistName.uppercase(),
                    ),
                style =
                    MotifTheme.typography.bodySB16,
            )
        } else {
            Text(
                text = "MOTIF",
                style =
                    MotifTheme.typography.rankR36.copy(
                        fontSize = 18.sp,
                        lineHeight = 18.sp,
                    ),
            )

            Text(
                text =
                    " " +
                            stringResource(
                                R.string
                                    .feature_motif_impl_product_purchase,
                            ),
                style =
                    MotifTheme.typography.bodySB16,
            )
        }
    }
}

private val ButtonHeight = 56.dp
private val ButtonCornerRadius = 12.dp

@Preview(showBackground = true)
@Composable
private fun PurchaseButtonPreview() {
    MotifTheme {
        PurchaseButton(
            isAvailable = false,
            artistName = "KARINA",
            onPurchaseClick = {},
            onMomentClick = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview(
    name = "Available",
    showBackground = true,
)
@Composable
private fun PurchaseButtonAvailablePreview() {
    MotifTheme {
        PurchaseButton(
            isAvailable = true,
            artistName = "KARINA",
            onPurchaseClick = {},
            onMomentClick = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}
