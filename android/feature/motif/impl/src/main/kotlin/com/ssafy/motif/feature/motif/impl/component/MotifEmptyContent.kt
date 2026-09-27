package com.ssafy.motif.feature.motif.impl.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ssafy.motif.core.designsystem.component.Text
import com.ssafy.motif.core.designsystem.theme.MotifTheme

@Composable
internal fun MotifEmptyContent(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = title,
            color = MotifTheme.colors.typo,
            style = MotifTheme.typography.titleSB18,
            textAlign = TextAlign.Center,
        )

        Spacer(
            modifier = Modifier.height(8.dp),
        )

        Text(
            text = description,
            color = MotifTheme.colors.gray500,
            style = MotifTheme.typography.bodyR14,
            textAlign = TextAlign.Center,
        )
    }
}