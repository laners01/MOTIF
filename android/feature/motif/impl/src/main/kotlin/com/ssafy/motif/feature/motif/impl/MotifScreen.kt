package com.ssafy.motif.feature.motif.impl

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.ssafy.motif.core.designsystem.component.GlassNavigationBarDefaults
import com.ssafy.motif.core.designsystem.theme.MotifTheme
import com.ssafy.motif.feature.motif.api.navigation.MotifSubMenu
import com.ssafy.motif.feature.motif.impl.archive.ArchiveScreen
import com.ssafy.motif.feature.motif.impl.assets.AssetListScreen
import com.ssafy.motif.feature.motif.impl.shop.ShopScreen

@Composable
fun MotifScreen(
    selectedMenu: MotifSubMenu,
    onProductClick: (
        artistId: Long,
        assetId: Long,
    ) -> Unit,
    onPassClick: (
        artistId: Long,
    ) -> Unit,
    modifier: Modifier = Modifier,
) {
    val navigationBarInset =
        WindowInsets.navigationBars
            .asPaddingValues()
            .calculateBottomPadding()

    val contentPadding =
        PaddingValues(
            bottom =
                navigationBarInset +
                        GlassNavigationBarDefaults.BottomMargin +
                        GlassNavigationBarDefaults.Height,
        )

    MotifSubContent(
        selectedMenu = selectedMenu,
        contentPadding = contentPadding,
        onProductClick = onProductClick,
        onPassClick = onPassClick,
        modifier =
            modifier.fillMaxSize(),
    )
}

@Composable
private fun MotifSubContent(
    selectedMenu: MotifSubMenu,
    contentPadding: PaddingValues,
    onProductClick: (
        artistId: Long,
        assetId: Long,
    ) -> Unit,
    onPassClick: (
        artistId: Long,
    ) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (selectedMenu) {
        MotifSubMenu.ASSETS -> {
            AssetListScreen(
                contentPadding = contentPadding,
                onProductClick = onProductClick,
                modifier = modifier.windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Top +
                                WindowInsetsSides.Horizontal,
                    ),
                ),
            )
        }

        MotifSubMenu.SHOP -> {
            ShopScreen(
                contentPadding = contentPadding,
                onProductClick = onProductClick,
                onPassClick = onPassClick,
                modifier = modifier.windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Top +
                                WindowInsetsSides.Horizontal,
                    ),
                ),
            )
        }

        MotifSubMenu.ARCHIVE -> {
            ArchiveScreen(
                contentPadding = contentPadding,
                modifier = modifier,
            )
        }
    }
}

@Preview(
    showBackground = true,
)
@Composable
private fun MotifScreenPreview() {
    MotifTheme {
        MotifScreen(
            selectedMenu =
                MotifSubMenu.ARCHIVE,
            onProductClick = {
                    _,
                    _,
                ->
            },
            onPassClick = {},
        )
    }
}