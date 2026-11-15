package com.unidownco.core.design.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

data class UniColorScheme(
    val background: Color,
    val surface: Color,
    val surfaceSubtle: Color,
    val primaryText: Color,
    val secondaryText: Color,
    val tertiaryText: Color,
    val primaryBorder: Color,
    val subtleBorder: Color,
    val accentYellow: Color,
    val accentYellowDark: Color,
    val success: Color,
    val successBackground: Color,
    val error: Color,
    val errorBackground: Color,
    val isDark: Boolean
)

val LocalUniColors = staticCompositionLocalOf<UniColorScheme> {
    error("No UniColorScheme provided")
}

private val LightUniColorScheme = UniColorScheme(
    background = UniColors.BackgroundLight,
    surface = UniColors.SurfaceLight,
    surfaceSubtle = UniColors.SurfaceSubtleLight,
    primaryText = UniColors.PrimaryTextLight,
    secondaryText = UniColors.SecondaryTextLight,
    tertiaryText = UniColors.TertiaryTextLight,
    primaryBorder = UniColors.PrimaryBorderLight,
    subtleBorder = UniColors.SubtleBorderLight,
    accentYellow = UniColors.AccentYellow,
    accentYellowDark = UniColors.AccentYellowDark,
    success = UniColors.Success,
    successBackground = UniColors.SuccessBackground,
    error = UniColors.Error,
    errorBackground = UniColors.ErrorBackground,
    isDark = false
)

private val DarkUniColorScheme = UniColorScheme(
    background = UniColors.BackgroundDark,
    surface = UniColors.SurfaceDark,
    surfaceSubtle = UniColors.SurfaceSubtleDark,
    primaryText = UniColors.PrimaryTextDark,
    secondaryText = UniColors.SecondaryTextDark,
    tertiaryText = UniColors.TertiaryTextDark,
    primaryBorder = UniColors.PrimaryBorderDark,
    subtleBorder = UniColors.SubtleBorderDark,
    accentYellow = UniColors.AccentYellow,
    accentYellowDark = UniColors.AccentYellowDark,
    success = UniColors.Success,
    successBackground = Color(0xFF14291D),
    error = UniColors.Error,
    errorBackground = Color(0xFF331414),
    isDark = true
)

private val MaterialLightScheme = lightColorScheme(
    primary = UniColors.PrimaryTextLight,
    onPrimary = UniColors.BackgroundLight,
    primaryContainer = UniColors.AccentYellow,
    onPrimaryContainer = Color.Black,
    secondary = UniColors.SecondaryTextLight,
    onSecondary = Color.White,
    background = UniColors.BackgroundLight,
    onBackground = UniColors.PrimaryTextLight,
    surface = UniColors.SurfaceLight,
    onSurface = UniColors.PrimaryTextLight,
    outline = UniColors.PrimaryBorderLight,
    outlineVariant = UniColors.SubtleBorderLight
)

private val MaterialDarkScheme = darkColorScheme(
    primary = UniColors.PrimaryTextDark,
    onPrimary = UniColors.BackgroundDark,
    primaryContainer = UniColors.AccentYellow,
    onPrimaryContainer = Color.Black,
    secondary = UniColors.SecondaryTextDark,
    onSecondary = Color.Black,
    background = UniColors.BackgroundDark,
    onBackground = UniColors.PrimaryTextDark,
    surface = UniColors.SurfaceDark,
    onSurface = UniColors.PrimaryTextDark,
    outline = UniColors.PrimaryBorderDark,
    outlineVariant = UniColors.SubtleBorderDark
)

@Composable
fun UniDownTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val uniColors = if (darkTheme) DarkUniColorScheme else LightUniColorScheme
    val materialScheme = if (darkTheme) MaterialDarkScheme else MaterialLightScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = uniColors.background.toArgb()
                window.navigationBarColor = uniColors.background.toArgb()
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !darkTheme
                controller.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(
        LocalUniColors provides uniColors
    ) {
        MaterialTheme(
            colorScheme = materialScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content
        )
    }
}

object UniDownTheme {
    val colors: UniColorScheme
        @Composable
        get() = LocalUniColors.current
    val spacing: UniSpacing
        get() = UniSpacing
    val shapes: UniShapes
        get() = UniShapes
}
