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
 * 하단 고정 MOTIF 구매 버튼. 구매 완료 상품이 선택되어 있으면 비활성화되고 문구가 구매 완료로 바뀐다.
 */
@Composable
internal fun PurchaseButton(
    isPurchased: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(ButtonHeight),
        enabled = !isPurchased,
        shape = RoundedCornerShape(ButtonCornerRadius),
        colors = MotifButtonDefaults.buttonColors(
            containerColor = MotifTheme.colors.pink,
            contentColor = MotifTheme.colors.white,
            disabledContainerColor = MotifTheme.colors.gray700,
            disabledContentColor = MotifTheme.colors.gray400,
        ),
    ) {
        if (isPurchased) {
            Text(
                text = stringResource(R.string.feature_motif_impl_purchased),
                style = MotifTheme.typography.bodySB16,
            )
        } else {
            // MOTIF는 로고 폰트, 구매는 본문 폰트
            Text(
                text = "MOTIF",
                style = MotifTheme.typography.rankR36.copy(
                    fontSize = 18.sp,
                    lineHeight = 18.sp,
                ),
            )

            Text(
                text = " " + stringResource(R.string.feature_motif_impl_product_purchase),
                style = MotifTheme.typography.bodySB16,
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
            isPurchased = false,
            onClick = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview(name = "Purchased", showBackground = true)
@Composable
private fun PurchaseButtonPurchasedPreview() {
    MotifTheme {
        PurchaseButton(
            isPurchased = true,
            onClick = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}
