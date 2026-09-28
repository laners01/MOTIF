package com.ssafy.motif.feature.artistdetail.impl.artistdetail.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ssafy.motif.core.designsystem.component.Button
import com.ssafy.motif.core.designsystem.component.MotifButtonDefaults
import com.ssafy.motif.core.designsystem.component.Text
import com.ssafy.motif.core.designsystem.theme.MotifTheme

@Composable
internal fun MotifEmptyContent(
    artistName: String,
    onCreateClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text(
            text = "아직 ${artistName}와 만든 모티프가 없어요",
            style = MotifTheme.typography.bodyR14,
            color = MotifTheme.colors.gray400,
        )

        Button(
            onClick = onCreateClick,
            shape = RoundedCornerShape(4.dp),
            colors = MotifButtonDefaults.buttonColors(
                containerColor = MotifTheme.colors.pink,
                contentColor = MotifTheme.colors.typo,
            ),
        ) {
            Text(
                text = "모티프 만들기",
                style = MotifTheme.typography.bodySB16,
            )
        }
    }
}