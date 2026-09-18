package com.ssafy.motif.feature.challenges.impl.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ssafy.motif.core.designsystem.component.HorizontalDivider
import com.ssafy.motif.core.designsystem.component.IconButton
import com.ssafy.motif.core.designsystem.component.ModifIconButtonDefaults
import com.ssafy.motif.core.designsystem.component.Surface
import com.ssafy.motif.core.designsystem.component.Text
import com.ssafy.motif.core.designsystem.component.bottomsheet.BottomSheet
import com.ssafy.motif.core.designsystem.component.bottomsheet.SheetState
import com.ssafy.motif.core.designsystem.component.bottomsheet.rememberBottomSheetState
import com.ssafy.motif.core.designsystem.icon.MotifIcons
import com.ssafy.motif.core.designsystem.theme.MotifTheme
import com.ssafy.motif.core.ui.formatter.formatCount
import com.ssafy.motif.feature.challenges.impl.R
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

@Composable
internal fun ChallengeDetailBottomSheet(
    description: String,
    viewCount: Long,
    likeCount: Long,
    createdAt: Long,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberBottomSheetState(
        skipPartiallyExpanded = false,
        partiallyExpandedFraction = DESCRIPTION_SHEET_FRACTION,
    ),
    additionalContent: @Composable () -> Unit = {},
) {
    val coroutineScope = rememberCoroutineScope()

    BottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        dragHandle = null,
        modifier = modifier,
    ) {
        ChallengeDetailHeader(
            onDismissClick = {
                coroutineScope.launch {
                    sheetState.hide()
                }
            },
        )

        HorizontalDivider(
            color = MotifTheme.colors.gray500,
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
        ) {
            Text(
                text = description,
                color = MotifTheme.colors.typo,
                style = MotifTheme.typography.bodyR16,
            )

            Spacer(
                modifier = Modifier.height(24.dp),
            )

            ChallengeStatistics(
                likeCount = likeCount,
                viewCount = viewCount,
                createdAt = createdAt,
            )

            additionalContent()
        }
    }
}

@Composable
private fun ChallengeDetailHeader(
    onDismissClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.challenge_description_title),
            color = MotifTheme.colors.typo,
            style = MotifTheme.typography.titleSB18,
        )

        Spacer(
            modifier = Modifier.weight(1f),
        )

        IconButton(
            onClick = onDismissClick,
            painter = painterResource(MotifIcons.Close),
            contentDescription = stringResource(
                R.string.challenge_description_close,
            ),
            size = 24.dp,
            colors = ModifIconButtonDefaults.iconButtonColors(
                contentColor = MotifTheme.colors.typo,
            ),
        )
    }
}

@Composable
private fun ChallengeStatistics(
    likeCount: Long,
    viewCount: Long,
    createdAt: Long,
    modifier: Modifier = Modifier,
) {
    val date = remember(createdAt) {
        Instant.ofEpochMilli(createdAt)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ChallengeStatisticItem(
            value = likeCount.formatCount(),
            label = stringResource(
                R.string.challenge_description_like,
            ),
            modifier = Modifier.weight(1f),
        )

        ChallengeStatisticItem(
            value = String.format(
                Locale.getDefault(),
                "%,d",
                viewCount,
            ),
            label = stringResource(
                R.string.challenge_description_view,
            ),
            modifier = Modifier.weight(1f),
        )

        ChallengeStatisticItem(
            value = stringResource(
                R.string.challenge_description_date,
                date.monthValue,
                date.dayOfMonth,
            ),
            label = stringResource(
                R.string.challenge_description_year,
                date.year,
            ),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ChallengeStatisticItem(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MotifTheme.colors.black,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = value,
                color = MotifTheme.colors.typo,
                style = MotifTheme.typography.titleSB20,
                maxLines = 1,
            )

            Spacer(
                modifier = Modifier.height(2.dp),
            )

            Text(
                text = label,
                color = MotifTheme.colors.gray300,
                style = MotifTheme.typography.bodySB12,
                maxLines = 1,
            )
        }
    }
}

@Preview(
    name = "Challenge Detail Bottom Sheet",
    showBackground = true,
)
@Composable
private fun ChallengeDetailBottomSheetPreview() {
    MotifTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MotifTheme.colors.black),
        ) {
            ChallengeDetailBottomSheet(
                description = "이번 챌린지 포인트는 손동작이랑 마지막 표정!",
                viewCount = 617_988L,
                likeCount = 12_000L,
                createdAt = 1_788_105_600_000L,
                onDismissRequest = {},
            )
        }
    }
}

private const val DESCRIPTION_SHEET_FRACTION = 0.45f