package com.ssafy.motif.feature.motif.impl.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ssafy.motif.core.designsystem.component.GlassNavigationBar
import com.ssafy.motif.core.designsystem.component.GlassNavigationBarBackButton
import com.ssafy.motif.core.designsystem.component.GlassNavigationBarItem
import com.ssafy.motif.core.designsystem.glass.GlassBackdropState
import com.ssafy.motif.core.designsystem.glass.glassBackdrop
import com.ssafy.motif.core.designsystem.glass.rememberGlassBackdropState
import com.ssafy.motif.core.designsystem.theme.MotifTheme
import com.ssafy.motif.feature.motif.impl.MotifSubMenu
import com.ssafy.motif.feature.motif.impl.R

/**
 * 모티프 화면의 서브 네비게이션 바.
 * 뒤로가기 버튼과 [MotifSubMenu] 항목들을 [GlassNavigationBar]에 배치한다.
 */
@Composable
internal fun MotifSubNavigationBar(
    backdropState: GlassBackdropState,
    selectedMenu: MotifSubMenu,
    onMenuClick: (MotifSubMenu) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassNavigationBar(
        backdropState = backdropState,
        modifier = modifier,
        leading = {
            GlassNavigationBarBackButton(
                onClick = onBackClick,
                contentDescription = stringResource(R.string.feature_motif_impl_back),
            )
        },
    ) {
        MotifSubMenu.entries.forEach { menu ->
            GlassNavigationBarItem(
                selected = menu == selectedMenu,
                onClick = { onMenuClick(menu) },
                icon = painterResource(menu.iconRes),
                label = stringResource(menu.labelRes),
            )
        }
    }
}

@Preview
@Composable
private fun MotifSubNavigationBarPreview() {
    MotifTheme {
        val backdropState = rememberGlassBackdropState()

        Box(
            modifier = Modifier.size(width = 400.dp, height = 200.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .glassBackdrop(backdropState)
                    .background(MotifTheme.colors.background),
            )

            MotifSubNavigationBar(
                backdropState = backdropState,
                selectedMenu = MotifSubMenu.ARCHIVE,
                onMenuClick = {},
                onBackClick = {},
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(20.dp),
            )
        }
    }
}
