package com.unidownco.core.design.theme

import androidx.compose.ui.graphics.Color

/**
 * UniDownco Color System
 * Identity: WHITE CANVAS. BLACK STRUCTURE. YELLOW ACCENT.
 * As defined in DESIGN.md
 */
object UniColors {
    // Light Mode Canvas
    val BackgroundLight = Color(0xFFFFFFFF)
    val SurfaceLight = Color(0xFFFFFFFF)
    val SurfaceSubtleLight = Color(0xFFF8F9FA)
    val PrimaryTextLight = Color(0xFF000000)
    val SecondaryTextLight = Color(0xFF333333)
    val TertiaryTextLight = Color(0xFF777777)
    val PrimaryBorderLight = Color(0xFF000000)
    val SubtleBorderLight = Color(0xFFE0E0E0)

    // Dark Mode Canvas
    val BackgroundDark = Color(0xFF0E0E10)
    val SurfaceDark = Color(0xFF18181B)
    val SurfaceSubtleDark = Color(0xFF222226)
    val PrimaryTextDark = Color(0xFFFFFFFF)
    val SecondaryTextDark = Color(0xFFD1D1D6)
    val TertiaryTextDark = Color(0xFF8E8E93)
    val PrimaryBorderDark = Color(0xFF333338)
    val SubtleBorderDark = Color(0xFF27272A)

    // Signature Accent Yellow (Consistent across Light & Dark)
    val AccentYellow = Color(0xFFFFD429)
    val AccentYellowDark = Color(0xFFE5BD1B)
    val AccentYellowLight = Color(0xFFFFE066)

    // Semantic States
    val Success = Color(0xFF107C41)
    val SuccessBackground = Color(0xFFE6F4EA)
    val Error = Color(0xFFD83B01)
    val ErrorBackground = Color(0xFFFCE8E6)
    val Warning = Color(0xFFB25E00)
    val WarningBackground = Color(0xFFFFF4CE)
    val Info = Color(0xFF0078D4)
    val InfoBackground = Color(0xFFEFF6FC)
}
