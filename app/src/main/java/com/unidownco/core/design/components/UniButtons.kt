package com.unidownco.core.design.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.unidownco.core.design.theme.AppTypography
import com.unidownco.core.design.theme.UniColors
import com.unidownco.core.design.theme.UniDownTheme
import com.unidownco.core.design.theme.UniShapes

/**
 * Primary action button:
 * Black background, White text, 1dp border, 10dp corners.
 * High visual prominence for main confirmations and primary screen actions.
 */
@Composable
fun UniPrimaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    content: @Composable RowScope.() -> Unit
) {
    val colors = UniDownTheme.colors
    val containerBg = if (colors.isDark) Color.White else Color.Black
    val contentFg = if (colors.isDark) Color.Black else Color.White

    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        enabled = enabled,
        shape = UniShapes.ButtonShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerBg,
            contentColor = contentFg,
            disabledContainerColor = if (colors.isDark) Color(0xFF2A2A2E) else Color(0xFFE5E5E5),
            disabledContentColor = Color(0xFF888888)
        ),
        border = BorderStroke(1.dp, if (enabled) containerBg else Color(0xFFCCCCCC)),
        contentPadding = contentPadding,
        content = content
    )
}

/**
 * Accent action button:
 * Signature Yellow (#FFD429), Black text, 1dp black border, 10dp corners.
 * Reserved strictly for key high-intent conversion actions (e.g., Download Selected Format).
 */
@Composable
fun UniAccentButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    content: @Composable RowScope.() -> Unit
) {
    val colors = UniDownTheme.colors
    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        enabled = enabled,
        shape = UniShapes.ButtonShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = UniColors.AccentYellow,
            contentColor = Color.Black,
            disabledContainerColor = if (colors.isDark) Color(0xFF333333) else Color(0xFFE5E5E5),
            disabledContentColor = Color(0xFF888888)
        ),
        border = BorderStroke(1.dp, if (enabled) Color.Black else Color(0xFFCCCCCC)),
        contentPadding = contentPadding,
        content = content
    )
}

/**
 * Secondary outlined button:
 * Canvas surface, PrimaryText, 1dp border, 10dp corners.
 * For secondary actions such as cancel, pause, settings updates.
 */
@Composable
fun UniOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    content: @Composable RowScope.() -> Unit
) {
    val colors = UniDownTheme.colors
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        enabled = enabled,
        shape = UniShapes.ButtonShape,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = colors.surface,
            contentColor = colors.primaryText,
            disabledContainerColor = colors.surfaceSubtle,
            disabledContentColor = colors.tertiaryText
        ),
        border = BorderStroke(1.dp, if (enabled) colors.primaryBorder else colors.subtleBorder),
        contentPadding = contentPadding,
        content = content
    )
}

/**
 * Destructive action button:
 * Red tinted container, bold red text/icon, 1dp red border, 10dp corners.
 * Used for deleting items, canceling active downloads, clearing logs.
 */
@Composable
fun UniDestructiveButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    content: @Composable RowScope.() -> Unit
) {
    val colors = UniDownTheme.colors
    Button(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        enabled = enabled,
        shape = UniShapes.ButtonShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.errorBackground,
            contentColor = colors.error,
            disabledContainerColor = colors.surfaceSubtle,
            disabledContentColor = colors.tertiaryText
        ),
        border = BorderStroke(1.dp, if (enabled) colors.error else colors.subtleBorder),
        contentPadding = contentPadding,
        content = content
    )
}

/**
 * Compact Action Button for In-Field Actions (e.g. PASTE, CLEAR, FILTER)
 */
@Composable
fun UniCompactButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPrimary: Boolean = false,
    enabled: Boolean = true
) {
    val colors = UniDownTheme.colors
    Button(
        onClick = onClick,
        modifier = modifier.height(34.dp),
        enabled = enabled,
        shape = UniShapes.BadgeShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isPrimary) UniColors.AccentYellow else colors.surfaceSubtle,
            contentColor = if (isPrimary) Color.Black else colors.primaryText,
            disabledContainerColor = colors.surfaceSubtle,
            disabledContentColor = colors.tertiaryText
        ),
        border = BorderStroke(1.dp, if (isPrimary) Color.Black else colors.subtleBorder),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            style = AppTypography.labelSmall
        )
    }
}
