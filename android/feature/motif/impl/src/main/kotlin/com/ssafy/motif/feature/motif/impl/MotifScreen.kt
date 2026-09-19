package com.ssafy.motif.feature.motif.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.motif.core.designsystem.component.GlassNavigationBarDefaults
import com.ssafy.motif.core.designsystem.glass.glassBackdrop
import com.ssafy.motif.core.designsystem.glass.rememberGlassBackdropState
import com.ssafy.motif.core.designsystem.theme.MotifTheme
import com.ssafy.motif.feature.motif.impl.archive.ArchiveScreen
import com.ssafy.motif.feature.motif.impl.assets.AssetListScreen
import com.ssafy.motif.feature.motif.impl.component.MotifSubNavigationBar
import com.ssafy.motif.feature.motif.impl.shop.ShopScreen

@Composable
fun MotifScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MotifViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    MotifScreen(
        uiState = uiState,
        onMenuClick = viewModel::selectMenu,
        onBackClick = onBackClick,
        modifier = modifier,
    )
}

@Composable
internal fun MotifScreen(
    uiState: MotifUiState,
    onMenuClick: (MotifSubMenu) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val backdropState = rememberGlassBackdropState()

    // 콘텐츠가 서브 네비게이션 바에 가려지지 않도록 시스템 인셋 + 바 하단 마진 + 바 높이만큼 여백을 준다.
    val navigationBarInset = WindowInsets.navigationBars
        .asPaddingValues()
        .calculateBottomPadding()
    val contentPadding = PaddingValues(
        bottom = navigationBarInset + SubNavigationBarBottomMargin + GlassNavigationBarDefaults.Height,
    )

    Box(
        modifier = modifier.fillMaxSize(),
    ) {
        // glassBackdrop 뒤에 background를 두어 배경색까지 blur 대상에 포함시킨다.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .glassBackdrop(backdropState)
                .background(MotifTheme.colors.background),
        ) {
            MotifSubContent(
                selectedMenu = uiState.selectedMenu,
                contentPadding = contentPadding,
            )
        }

        MotifSubNavigationBar(
            backdropState = backdropState,
            selectedMenu = uiState.selectedMenu,
            onMenuClick = onMenuClick,
            onBackClick = onBackClick,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(
                    start = SubNavigationBarHorizontalMargin,
                    end = SubNavigationBarHorizontalMargin,
                    bottom = SubNavigationBarBottomMargin,
                ),
        )
    }
}

@Composable
private fun MotifSubContent(
    selectedMenu: MotifSubMenu,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    when (selectedMenu) {
        MotifSubMenu.ASSETS -> AssetListScreen(
            contentPadding = contentPadding,
            modifier = modifier,
        )

        MotifSubMenu.SHOP -> ShopScreen(
            contentPadding = contentPadding,
            modifier = modifier,
        )

        MotifSubMenu.ARCHIVE -> ArchiveScreen(
            contentPadding = contentPadding,
            modifier = modifier,
        )
    }
}

private val SubNavigationBarHorizontalMargin = 20.dp
private val SubNavigationBarBottomMargin = 16.dp

@Preview
@Composable
private fun MotifScreenPreview() {
    MotifTheme {
        MotifScreen(
            uiState = MotifUiState(selectedMenu = MotifSubMenu.ARCHIVE),
            onMenuClick = {},
            onBackClick = {},
        )
    }
}
