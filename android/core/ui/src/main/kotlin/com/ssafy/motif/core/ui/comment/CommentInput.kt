package com.ssafy.motif.core.ui.comment

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.ssafy.motif.core.designsystem.component.HorizontalDivider
import com.ssafy.motif.core.designsystem.component.Icon
import com.ssafy.motif.core.designsystem.component.Text
import com.ssafy.motif.core.designsystem.icon.MotifIcons
import com.ssafy.motif.core.designsystem.theme.MotifTheme
import com.ssafy.motif.core.ui.R

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CommentInput(
    onSendClick: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    var isEditing by remember {
        mutableStateOf(false)
    }

    var wasImeVisible by remember {
        mutableStateOf(false)
    }

    var inputValue by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(""))
    }

    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val isImeVisible = WindowInsets.isImeVisible
    val imeInsets = WindowInsets.ime
    val navigationBarInsets = WindowInsets.navigationBars
    val sendEnabled = inputValue.text.isNotBlank()

    val sendComment = {
        val text = inputValue.text.trim()

        if (text.isNotEmpty()) {
            onSendClick(text)
            inputValue = TextFieldValue("")
        }
    }

    LaunchedEffect(isEditing) {
        if (isEditing) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    LaunchedEffect(isEditing, isImeVisible) {
        when {
            !isEditing -> wasImeVisible = false
            isImeVisible -> wasImeVisible = true
            wasImeVisible -> {
                isEditing = false
                wasImeVisible = false
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .offset {
                val keyboardOffset = if (isEditing) {
                    (imeInsets.getBottom(this) - navigationBarInsets
                        .getBottom(this))
                        .coerceAtLeast(0)
                } else {
                    0
                }

                IntOffset(x = 0, y = -keyboardOffset)
            }
            .background(MotifTheme.colors.background),
    ) {
        HorizontalDivider(
            color = MotifTheme.colors.gray500.copy(alpha = 0.5f),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isEditing) {
                CommentTextField(
                    value = inputValue,
                    onValueChange = {
                        inputValue = it
                    },
                    onSendClick = sendComment,
                    sendEnabled = sendEnabled,
                    focusRequester = focusRequester,
                    modifier = Modifier.weight(1f),
                )
            } else {
                CommentInputButton(
                    value = inputValue.text,
                    placeholder = placeholder,
                    onClick = {
                        inputValue = inputValue.copy(
                            selection = TextRange(inputValue.text.length),
                        )
                        isEditing = true
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun CommentInputButton(
    value: String,
    placeholder: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(INPUT_SHAPE)
            .background(MotifTheme.colors.black)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            text = value.ifEmpty { placeholder },
            color = MotifTheme.colors.typo.copy(alpha = if (value.isEmpty()) 0.5f else 1f),
            style = MotifTheme.typography.bodySB16,
            maxLines = 1,
        )
    }
}

@Composable
private fun CommentTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onSendClick: () -> Unit,
    sendEnabled: Boolean,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .weight(1f)
                .background(
                    color = MotifTheme.colors.black,
                    shape = INPUT_SHAPE,
                )
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .focusRequester(focusRequester),
            textStyle = MotifTheme.typography.bodySB16.copy(
                color = MotifTheme.colors.typo,
            ),
            cursorBrush = SolidColor(MotifTheme.colors.typo),
            maxLines = 4,
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Send,
            ),
            keyboardActions = KeyboardActions(
                onSend = {
                    if (sendEnabled) {
                        onSendClick()
                    }
                },
            ),
        )

        Spacer(
            modifier = Modifier.width(12.dp),
        )

        CommentSendButton(
            enabled = sendEnabled,
            onClick = onSendClick,
        )
    }
}

@Composable
private fun CommentSendButton(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(SEND_BUTTON_SIZE)
            .clip(CircleShape)
            .background(
                if (enabled) {
                    MotifTheme.colors.pink
                } else {
                    MotifTheme.colors.black
                },
            )
            .clickable(
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(MotifIcons.Send),
            contentDescription = stringResource(R.string.comment_send_content_description),
            tint = if (enabled) {
                MotifTheme.colors.white
            } else {
                MotifTheme.colors.gray500
            },
            modifier = Modifier.size(SEND_ICON_SIZE),
        )
    }
}

@Preview(
    name = "CommentInput",
    showBackground = true,
)
@Composable
private fun CommentInputPreview() {
    MotifTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MotifTheme.colors.background)
                .padding(vertical = 24.dp), contentAlignment = Alignment.BottomCenter
        ) {
            CommentInput(
                onSendClick = {},
                placeholder = "댓글을 남겨보세요",
            )
        }
    }
}

private val INPUT_SHAPE = RoundedCornerShape(24.dp)
private val SEND_BUTTON_SIZE = 36.dp
private val SEND_ICON_SIZE = 20.dp