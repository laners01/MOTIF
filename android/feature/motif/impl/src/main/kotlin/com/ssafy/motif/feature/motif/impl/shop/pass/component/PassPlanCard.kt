package com.ssafy.motif.feature.motif.impl.shop.pass.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ssafy.motif.core.designsystem.component.Button
import com.ssafy.motif.core.designsystem.component.MotifButtonDefaults
import com.ssafy.motif.core.designsystem.component.Surface
import com.ssafy.motif.core.designsystem.component.Text
import com.ssafy.motif.core.designsystem.theme.MotifTheme
import com.ssafy.motif.core.model.shop.PassPlan
import com.ssafy.motif.core.ui.formatter.formatPrice
import com.ssafy.motif.feature.motif.impl.R

/**
 * PASS 결제 플랜 카드. 기간 + 부가 안내 / 구매 버튼 / 월 환산 가격.
 */
@Composable
internal fun PassPlanCard(
    plan: PassPlan,
    onPurchaseClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CardCornerRadius),
        color = MotifTheme.colors.black,
        contentColor = MotifTheme.colors.white,
        contentPadding = PaddingValues(
            horizontal = 20.dp,
            vertical = 16.dp,
        ),
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = planTitle(plan.months),
                        style = MotifTheme.typography.titleSB20,
                    )

                    plan.note?.let { note ->
                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = note,
                            color = MotifTheme.colors.gray400,
                            style = MotifTheme.typography.captionR12,
                        )
                    }
                }

                Button(
                    onClick = { onPurchaseClick(plan.id) },
                    shape = RoundedCornerShape(percent = 50),
                    colors = MotifButtonDefaults.buttonColors(
                        containerColor = MotifTheme.colors.pink,
                        contentColor = MotifTheme.colors.white,
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = stringResource(R.string.feature_motif_impl_product_purchase),
                        style = MotifTheme.typography.bodySB14,
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.align(Alignment.End),
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    text = plan.monthlyPrice.formatPrice(),
                    style = MotifTheme.typography.titleSB18,
                )

                Text(
                    text = " " + stringResource(R.string.feature_motif_impl_pass_plan_per_month),
                    color = MotifTheme.colors.gray400,
                    style = MotifTheme.typography.captionR12,
                )
            }
        }
    }
}

@Composable
private fun planTitle(months: Int): String =
    if (months == 1) {
        stringResource(R.string.feature_motif_impl_pass_plan_month, months)
    } else {
        stringResource(R.string.feature_motif_impl_pass_plan_months, months)
    }

private val CardCornerRadius = 16.dp

@Preview(showBackground = true)
@Composable
private fun PassPlanCardPreview() {
    MotifTheme {
        PassPlanCard(
            plan = PassPlan(id = 1L, months = 1, monthlyPrice = 9_900),
            onPurchaseClick = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview(name = "With note", showBackground = true)
@Composable
private fun PassPlanCardWithNotePreview() {
    MotifTheme {
        PassPlanCard(
            plan = PassPlan(id = 3L, months = 12, monthlyPrice = 6_000, note = "+ 시크릿 에셋"),
            onPurchaseClick = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}
