package com.ssafy.motif.feature.motif.impl.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ssafy.motif.core.designsystem.component.LoadingWheel
import com.ssafy.motif.core.designsystem.theme.MotifTheme

/**
 * 목록 하단 추가 로드 중에 표시하는 로딩 인디케이터.
 */
@Composable
internal fun LoadingFooter(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        LoadingWheel()
    }
}

@Preview(showBackground = true)
@Composable
private fun LoadingFooterPreview() {
    MotifTheme {
        LoadingFooter()
    }
}
