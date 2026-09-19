package com.ssafy.motif.feature.motif.impl.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.motif.core.designsystem.component.Text
import com.ssafy.motif.core.designsystem.theme.MotifTheme

/**
 * 모티프 서브 화면 상단의 로고 폰트 타이틀 (MOTIF, SHOP 등).
 */
@Composable
internal fun MotifScreenTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = MotifTheme.colors.white,
            style = MotifTheme.typography.rankR36.copy(
                fontSize = 28.sp,
                lineHeight = 32.sp,
            ),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun MotifScreenTitlePreview() {
    MotifTheme {
        MotifScreenTitle(text = "SHOP")
    }
}
