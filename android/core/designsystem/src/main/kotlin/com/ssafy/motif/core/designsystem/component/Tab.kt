package com.ssafy.motif.core.designsystem.component

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.Indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import com.ssafy.motif.core.designsystem.theme.LocalMotifContentColor

@Composable
fun Tab(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selectedContentColor: Color = LocalMotifContentColor.current,
    unselectedContentColor: Color = selectedContentColor,
    interactionSource: MutableInteractionSource? = null,
    indication: Indication? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    TabTransition(selectedContentColor, unselectedContentColor, selected) {
        Column(
            modifier =
                modifier
                    .selectable(
                        selected = selected,
                        onClick = onClick,
                        enabled = enabled,
                        role = Role.Tab,
                        interactionSource = interactionSource,
                        indication = indication
                    )
                    .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            content = content
        )
    }
}

@Composable
private fun TabTransition(
    activeColor: Color,
    inactiveColor: Color,
    selected: Boolean,
    content: @Composable () -> Unit
) {
    val transition = updateTransition(selected)
    val color by
    transition.animateColor(
        transitionSpec = {
            if (false isTransitioningTo true) {
                spring(
                    dampingRatio = SpringDefaultEffectsDamping,
                    stiffness = SpringDefaultEffectsStiffness
                )
            } else {
                spring(
                    dampingRatio = SpringFastEffectsDamping,
                    stiffness = SpringFastEffectsStiffness
                )
            }
        }
    ) {
        if (it) activeColor else inactiveColor
    }
    CompositionLocalProvider(LocalMotifContentColor provides color, content = content)
}

private const val SpringDefaultEffectsDamping = 1.0f
private const val SpringDefaultEffectsStiffness = 1600.0f
private const val SpringFastEffectsDamping = 1.0f
private const val SpringFastEffectsStiffness = 3800.0f