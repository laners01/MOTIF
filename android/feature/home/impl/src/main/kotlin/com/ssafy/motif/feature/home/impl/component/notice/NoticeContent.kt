package com.ssafy.motif.feature.home.impl.component.notice

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.ssafy.motif.core.designsystem.component.Text
import com.ssafy.motif.core.designsystem.theme.MotifTheme
import kotlin.math.floor

@Composable
internal fun NoticeCollapsedContent(
    content: String,
    onExpandClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val textStyle = MotifTheme.typography.bodyR12
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = 16.dp,
                end = 16.dp,
                bottom = 12.dp,
            ),
    ) {
        val maxWidthPx = with(density) {
            maxWidth.roundToPx()
        }

        val maxHeightPx = with(density) {
            maxHeight.toPx()
        }

        val lineHeightPx = with(density) {
            textStyle.lineHeight.toPx()
        }

        val maxLines =
            if (lineHeightPx <= 0f) {
                1
            } else {
                floor(
                    maxHeightPx / lineHeightPx,
                )
                    .toInt()
                    .coerceAtLeast(1)
            }

        val displayText = remember(
            content,
            maxWidthPx,
            maxLines,
            textStyle,
        ) {
            buildCollapsedNoticeText(
                content = content,
                maxWidthPx = maxWidthPx,
                maxLines = maxLines,
                textStyle = textStyle,
                textMeasurer = textMeasurer,
            )
        }

        var textLayoutResult by remember {
            mutableStateOf<TextLayoutResult?>(null)
        }

        Text(
            text = displayText,
            color = MotifTheme.colors.typo,
            style = textStyle,
            maxLines = maxLines,
            overflow = TextOverflow.Clip,
            onTextLayout = {
                textLayoutResult = it
            },
            modifier = Modifier.pointerInput(
                displayText,
            ) {
                detectTapGestures { position ->
                    val layoutResult =
                        textLayoutResult
                            ?: return@detectTapGestures

                    val offset =
                        layoutResult.getOffsetForPosition(
                            position,
                        )

                    val moreAnnotation =
                        displayText.getStringAnnotations(
                            tag = MORE_TAG,
                            start = offset,
                            end = offset,
                        )

                    if (moreAnnotation.isNotEmpty()) {
                        onExpandClick()
                    }
                }
            },
        )
    }
}

@Composable
internal fun NoticeExpandedContent(
    content: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = content,
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = 16.dp,
                end = 16.dp,
                bottom = 12.dp,
            ),
        color = MotifTheme.colors.typo,
        style = MotifTheme.typography.bodyR12,
    )
}

private fun buildCollapsedNoticeText(
    content: String,
    maxWidthPx: Int,
    maxLines: Int,
    textStyle: TextStyle,
    textMeasurer: TextMeasurer,
): AnnotatedString {
    val originalResult = textMeasurer.measure(
        text = content,
        style = textStyle,
        maxLines = maxLines,
        overflow = TextOverflow.Clip,
        constraints = Constraints(
            maxWidth = maxWidthPx,
        ),
    )

    if (!originalResult.hasVisualOverflow) {
        return AnnotatedString(content)
    }

    var low = 0
    var high = content.length

    var bestText = buildMoreText(
        prefix = "",
    )

    while (low <= high) {
        val middle = (low + high) / 2

        val prefix = content
            .substring(
                startIndex = 0,
                endIndex = middle,
            )
            .trimEnd()

        val candidate = buildMoreText(
            prefix = prefix,
        )

        val result = textMeasurer.measure(
            text = candidate,
            style = textStyle,
            maxLines = maxLines,
            overflow = TextOverflow.Clip,
            constraints = Constraints(
                maxWidth = maxWidthPx,
            ),
        )

        if (result.hasVisualOverflow) {
            high = middle - 1
        } else {
            bestText = candidate
            low = middle + 1
        }
    }

    return bestText
}

private fun buildMoreText(
    prefix: String,
): AnnotatedString =
    buildAnnotatedString {
        append(prefix)
        append(" ... ")

        pushStringAnnotation(
            tag = MORE_TAG,
            annotation = MORE_TAG,
        )

        withStyle(
            style = SpanStyle(
                color = NoticeDefaults.AccentColor,
                fontWeight = FontWeight.SemiBold,
            ),
        ) {
            append("더보기")
        }

        pop()
    }

private const val MORE_TAG = "more"