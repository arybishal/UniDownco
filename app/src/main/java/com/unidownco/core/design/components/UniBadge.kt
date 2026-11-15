package com.unidownco.core.design.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.unidownco.core.design.theme.AppTypography
import com.unidownco.core.design.theme.UniColors
import com.unidownco.core.design.theme.UniDownTheme
import com.unidownco.core.design.theme.UniMonoTypography
import com.unidownco.core.design.theme.UniShapes

/**
 * Editorial badges for Status (PENDING, DOWNLOADING, COMPLETED, FAILED)
 * and Technical Specifications (HEVC, HDR10, 60FPS, 4K)
 */
@Composable
fun UniStatusBadge(
    status: String,
    modifier: Modifier = Modifier
) {
    val colors = UniDownTheme.colors
    val (bgColor, textColor, borderColor) = when (status.uppercase()) {
        "DOWNLOADING" -> Triple(UniColors.AccentYellow, Color.Black, Color.Black)
        "FETCHING_METADATA" -> Triple(colors.surfaceSubtle, colors.primaryText, colors.subtleBorder)
        "COMPLETED" -> Triple(colors.successBackground, colors.success, colors.success)
        "FAILED" -> Triple(colors.errorBackground, colors.error, colors.error)
        "PAUSED" -> Triple(colors.surfaceSubtle, colors.secondaryText, colors.subtleBorder)
        "CANCELLED" -> Triple(colors.surfaceSubtle, colors.tertiaryText, colors.subtleBorder)
        else -> Triple(colors.surfaceSubtle, colors.tertiaryText, colors.subtleBorder)
    }

    Box(
        modifier = modifier
            .background(bgColor, UniShapes.BadgeShape)
            .border(BorderStroke(1.dp, borderColor), UniShapes.BadgeShape)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = status.uppercase(),
            style = AppTypography.labelSmall,
            color = textColor
        )
    }
}

/**
 * Technical Specification Badge using Monospace Typography
 */
@Composable
fun UniTechBadge(
    label: String,
    modifier: Modifier = Modifier,
    isHighlighted: Boolean = false
) {
    val colors = UniDownTheme.colors
    val bg = if (isHighlighted) UniColors.AccentYellow else colors.surfaceSubtle
    val fg = if (isHighlighted) Color.Black else colors.primaryText
    val border = if (isHighlighted) Color.Black else colors.subtleBorder

    Box(
        modifier = modifier
            .background(bg, UniShapes.BadgeShape)
            .border(BorderStroke(1.dp, border), UniShapes.BadgeShape)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            style = UniMonoTypography.TechnicalSmall,
            color = fg
        )
    }
}
