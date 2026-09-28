package com.ssafy.motif.feature.motif.impl.shop.pass.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ssafy.motif.core.designsystem.component.Text
import com.ssafy.motif.core.designsystem.theme.MotifTheme
import com.ssafy.motif.feature.motif.impl.R

/**
 * PASS 이용 안내 정적 텍스트.
 */
@Composable
internal fun PassGuideSection(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text = stringResource(R.string.feature_motif_impl_product_guide_usage_title),
            color = MotifTheme.colors.typo,
            style = MotifTheme.typography.bodySB14,
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = stringResource(R.string.feature_motif_impl_pass_guide_body),
            color = MotifTheme.colors.gray400,
            style = MotifTheme.typography.captionR12,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PassGuideSectionPreview() {
    MotifTheme {
        PassGuideSection(
            modifier = Modifier.padding(16.dp),
        )
    }
}
