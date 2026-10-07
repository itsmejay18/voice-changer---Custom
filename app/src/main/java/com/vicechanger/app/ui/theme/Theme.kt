package com.vicechanger.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import com.vicechanger.app.settings.ThemeMode

private val DarkScheme = darkColorScheme(
    primary = ViceColors.Rose,
    onPrimary = ViceColors.TextPrimary,
    primaryContainer = ViceColors.RoseDeep,
    onPrimaryContainer = ViceColors.TextPrimary,
    secondary = ViceColors.Violet,
    onSecondary = ViceColors.TextPrimary,
    tertiary = ViceColors.Mint,
    onTertiary = ViceColors.Ink,
    background = ViceColors.Ink,
    onBackground = ViceColors.TextPrimary,
    surface = ViceColors.Surface,
    onSurface = ViceColors.TextPrimary,
    surfaceVariant = ViceColors.SurfaceHigh,
    onSurfaceVariant = ViceColors.TextSecondary,
    outline = ViceColors.Outline,
    error = ViceColors.Red,
    onError = ViceColors.TextPrimary,
)

private val LightScheme = lightColorScheme(
    primary = ViceColors.RoseDeep,
    onPrimary = ViceColors.PaperSurface,
    primaryContainer = ViceColors.RoseSoft,
    onPrimaryContainer = ViceColors.PaperText,
    secondary = ViceColors.Violet,
    onSecondary = ViceColors.PaperSurface,
    tertiary = ViceColors.Mint,
    onTertiary = ViceColors.PaperText,
    background = ViceColors.Paper,
    onBackground = ViceColors.PaperText,
    surface = ViceColors.PaperSurface,
    onSurface = ViceColors.PaperText,
    surfaceVariant = ViceColors.PaperRaised,
    onSurfaceVariant = ViceColors.PaperTextSecondary,
    outline = ViceColors.PaperOutline,
    error = ViceColors.Red,
    onError = ViceColors.PaperSurface,
)

@Composable
fun ViceChangerTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (dark) DarkScheme else LightScheme,
        typography = ViceTypography,
        content = content,
    )
}
