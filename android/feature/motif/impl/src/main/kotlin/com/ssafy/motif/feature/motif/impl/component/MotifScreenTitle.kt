package com.ssafy.motif.feature.motif.impl.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
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
    Text(
        text = text,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = TitleVerticalPadding)
            // Paytone One은 descent가 커서 대문자만 쓰면 글자가 텍스트 박스 위쪽에 몰린다.
            // 박스가 아니라 글자가 세로 중앙에 오도록 시각 보정한다.
            .offset(y = TitleOpticalOffset),
        color = MotifTheme.colors.white,
        textAlign = TextAlign.Center,
        style = MotifTheme.typography.rankR36.copy(
            fontSize = 28.sp,
            lineHeight = 28.sp,
            // 로고 폰트는 ascent가 커서 기본 lineHeight 안에서 글자가 위로 떠 보인다.
            // 위아래 여백을 잘라내고 글자 높이에 딱 맞춰 세로 중앙을 맞춘다.
            lineHeightStyle = LineHeightStyle(
                alignment = LineHeightStyle.Alignment.Center,
                trim = LineHeightStyle.Trim.Both,
            ),
        ),
    )
}

// 화면(상태바 인셋 아래) 상단과 아래 콘텐츠로부터 타이틀까지의 거리
private val TitleVerticalPadding = 20.dp

// 폰트 메트릭 보정값. 대문자 로고가 시각적으로 중앙에 오도록 아래로 내리는 양
private val TitleOpticalOffset = 2.dp

@Preview(showBackground = true)
@Composable
private fun MotifScreenTitlePreview() {
    MotifTheme {
        MotifScreenTitle(text = "SHOP")
    }
}
