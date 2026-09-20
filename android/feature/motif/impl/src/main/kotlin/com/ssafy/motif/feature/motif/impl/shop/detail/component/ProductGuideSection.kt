package com.ssafy.motif.feature.motif.impl.shop.detail.component

import androidx.compose.foundation.layout.Arrangement
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
 * 소개 / 이용 안내 / 콘텐츠 관련 유의사항 정적 안내 텍스트.
 * 문구는 strings.xml에 두었고, 서버 전달로 바뀌면 모델로 옮긴다.
 */
@Composable
internal fun ProductGuideSection(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        GuideItem(
            title = stringResource(R.string.feature_motif_impl_product_guide_intro_title),
            body = stringResource(R.string.feature_motif_impl_product_guide_intro_body),
        )

        GuideItem(
            title = stringResource(R.string.feature_motif_impl_product_guide_usage_title),
            body = stringResource(R.string.feature_motif_impl_product_guide_usage_body),
        )

        GuideItem(
            title = stringResource(R.string.feature_motif_impl_product_guide_notice_title),
            body = stringResource(R.string.feature_motif_impl_product_guide_notice_body),
        )
    }
}

@Composable
private fun GuideItem(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text = title,
            color = MotifTheme.colors.typo,
            style = MotifTheme.typography.bodySB14,
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = body,
            color = MotifTheme.colors.gray400,
            style = MotifTheme.typography.captionR12,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ProductGuideSectionPreview() {
    MotifTheme {
        ProductGuideSection(
            modifier = Modifier.padding(16.dp),
        )
    }
}
