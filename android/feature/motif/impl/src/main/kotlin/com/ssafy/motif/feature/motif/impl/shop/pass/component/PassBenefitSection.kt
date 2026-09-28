package com.ssafy.motif.feature.motif.impl.shop.pass.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ssafy.motif.core.designsystem.component.Text
import com.ssafy.motif.core.designsystem.theme.MotifTheme
import com.ssafy.motif.feature.motif.impl.R

/**
 * MOTIF PASS 혜택 — 타이틀 + 글머리 목록. 문구는 strings.xml 정적 리소스.
 */
@Composable
internal fun PassBenefitSection(
    modifier: Modifier = Modifier,
) {
    val benefits = listOf(
        stringResource(R.string.feature_motif_impl_pass_benefit_1),
        stringResource(R.string.feature_motif_impl_pass_benefit_2),
        stringResource(R.string.feature_motif_impl_pass_benefit_3),
        stringResource(R.string.feature_motif_impl_pass_benefit_4),
    )

    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text = stringResource(R.string.feature_motif_impl_pass_benefit_title),
            color = MotifTheme.colors.white,
            style = MotifTheme.typography.titleSB18,
        )

        Spacer(modifier = Modifier.height(12.dp))

        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            benefits.forEach { benefit ->
                Row {
                    Text(
                        text = "•",
                        color = MotifTheme.colors.typo,
                        style = MotifTheme.typography.bodyR14,
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = benefit,
                        color = MotifTheme.colors.typo,
                        style = MotifTheme.typography.bodyR14,
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PassBenefitSectionPreview() {
    MotifTheme {
        PassBenefitSection(
            modifier = Modifier.padding(16.dp),
        )
    }
}
