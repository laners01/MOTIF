package com.ssafy.motif.feature.motif.impl.shop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ssafy.motif.core.designsystem.component.Text
import com.ssafy.motif.core.designsystem.theme.MotifTheme
import com.ssafy.motif.feature.motif.impl.R

/**
 * 모티프 서브 네비게이션의 샵 화면.
 *
 * @param contentPadding 서브 네비게이션 바에 가려지지 않도록 콘텐츠에 적용할 여백.
 */
@Composable
internal fun ShopScreen(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.feature_motif_impl_shop),
            color = MotifTheme.colors.typo,
            style = MotifTheme.typography.titleSB18,
        )
    }
}

@Preview
@Composable
private fun ShopScreenPreview() {
    MotifTheme {
        ShopScreen(
            contentPadding = PaddingValues(0.dp),
        )
    }
}
