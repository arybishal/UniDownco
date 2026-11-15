package com.unidownco.core.design.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.unidownco.core.design.theme.UniDownTheme
import com.unidownco.core.design.theme.UniShapes
import com.unidownco.core.design.theme.UniSpacing

/**
 * Editorial structural card with 1dp solid border, 12dp corners, surface background.
 */
@Composable
fun UniCard(
    modifier: Modifier = Modifier,
    backgroundColor: Color = UniDownTheme.colors.surface,
    borderColor: Color = UniDownTheme.colors.primaryBorder,
    borderWidth: Float = 1f,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val clickableModifier = if (onClick != null) {
        Modifier.clickable(onClick = onClick)
    } else Modifier

    Box(
        modifier = modifier
            .clip(UniShapes.CardShape)
            .background(backgroundColor, UniShapes.CardShape)
            .border(
                border = BorderStroke(borderWidth.dp, borderColor),
                shape = UniShapes.CardShape
            )
            .then(clickableModifier)
            .padding(UniSpacing.cardContentPadding)
    ) {
        content()
    }
}
