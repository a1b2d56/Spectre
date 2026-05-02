package com.spectre.app.core.ui.theme

import android.app.Activity
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

enum class SpectreTheme {
    MIDNIGHT,   // AMOLED pure black + indigo — default
    PHANTOM,    // Deep navy + violet
    OBSIDIAN,   // Charcoal + cyan
    NORD,       // Arctic blue-grey
    ROSEWOOD,   // Dark crimson + rose
    SAGE,       // Forest green
    LIGHT       // Material You light (dynamic)
}

private val MidnightScheme = darkColorScheme(
    primary              = MidnightPrimary,
    onPrimary            = MidnightOnPrimary,
    primaryContainer     = Color(0xFF312E81),
    onPrimaryContainer   = Color(0xFFE0E7FF),
    secondary            = MidnightSecondary,
    background           = MidnightBackground,
    surface              = MidnightSurface,
    surfaceVariant       = Color(0xFF0F0F18),
    surfaceContainerLow  = Color(0xFF050508),
    surfaceContainerHigh = Color(0xFF14141F),
    onSurface            = Color(0xFFE2E2F0),
    onBackground         = Color(0xFFE2E2F0),
    onSurfaceVariant     = Color(0xFF9696B0),
    outline              = Color(0xFF2D2D45),
    error                = DangerRed,
)

private val PhantomScheme = darkColorScheme(
    primary              = PhantomPrimary,
    onPrimary            = PhantomOnPrimary,
    primaryContainer     = Color(0xFF3B0764),
    onPrimaryContainer   = Color(0xFFEDE9FE),
    secondary            = PhantomSecondary,
    background           = PhantomBackground,
    surface              = PhantomSurface,
    surfaceVariant       = PhantomSurfaceVar,
    surfaceContainerLow  = Color(0xFF0D1117),
    surfaceContainerHigh = Color(0xFF2D333B),
    onSurface            = Color(0xFFCDD9E5),
    onBackground         = Color(0xFFCDD9E5),
    onSurfaceVariant     = Color(0xFF8B949E),
    outline              = PhantomOutline,
    error                = DangerRed,
)

private val ObsidianScheme = darkColorScheme(
    primary              = ObsidianPrimary,
    onPrimary            = ObsidianOnPrimary,
    primaryContainer     = Color(0xFF164E63),
    onPrimaryContainer   = Color(0xFFCFFAFE),
    secondary            = ObsidianSecondary,
    background           = ObsidianBackground,
    surface              = ObsidianSurface,
    surfaceVariant       = Color(0xFF242424),
    surfaceContainerLow  = Color(0xFF0A0A0A),
    surfaceContainerHigh = Color(0xFF262626),
    onSurface            = Color(0xFFE5E7EB),
    onBackground         = Color(0xFFE5E7EB),
    onSurfaceVariant     = Color(0xFF9CA3AF),
    outline              = Color(0xFF374151),
    error                = DangerRed,
)

private val NordScheme = darkColorScheme(
    primary              = NordPrimary,
    onPrimary            = NordOnPrimary,
    primaryContainer     = Color(0xFF434C5E),
    onPrimaryContainer   = Color(0xFFECEFF4),
    secondary            = NordSecondary,
    background           = NordBackground,
    surface              = NordSurface,
    surfaceVariant       = Color(0xFF434C5E),
    surfaceContainerLow  = Color(0xFF2B3241),
    surfaceContainerHigh = Color(0xFF434C5E),
    onSurface            = Color(0xFFECEFF4),
    onBackground         = Color(0xFFECEFF4),
    onSurfaceVariant     = Color(0xFFD8DEE9),
    outline              = Color(0xFF4C566A),
    error                = Color(0xFFBF616A),
)

private val RosewoodScheme = darkColorScheme(
    primary              = RosewoodPrimary,
    onPrimary            = RosewoodOnPrimary,
    primaryContainer     = Color(0xFF881337),
    onPrimaryContainer   = Color(0xFFFFE4E6),
    secondary            = RosewoodSecondary,
    background           = RosewoodBackground,
    surface              = RosewoodSurface,
    surfaceVariant       = Color(0xFF2A1414),
    surfaceContainerLow  = Color(0xFF0A0505),
    surfaceContainerHigh = Color(0xFF2E1818),
    onSurface            = Color(0xFFEDD8D8),
    onBackground         = Color(0xFFEDD8D8),
    onSurfaceVariant     = Color(0xFFB08080),
    outline              = Color(0xFF5A2020),
    error                = DangerRed,
)

private val SageScheme = darkColorScheme(
    primary              = SagePrimary,
    onPrimary            = SageOnPrimary,
    primaryContainer     = Color(0xFF14532D),
    onPrimaryContainer   = Color(0xFFDCFCE7),
    secondary            = SageSecondary,
    background           = SageBackground,
    surface              = SageSurface,
    surfaceVariant       = Color(0xFF162416),
    surfaceContainerLow  = Color(0xFF060D06),
    surfaceContainerHigh = Color(0xFF1A2C1A),
    onSurface            = Color(0xFFD4E8D4),
    onBackground         = Color(0xFFD4E8D4),
    onSurfaceVariant     = Color(0xFF80A880),
    outline              = Color(0xFF284028),
    error                = DangerRed,
)

@Composable
fun SpectreTheme(
    appTheme: SpectreTheme = SpectreTheme.MIDNIGHT,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current

    val colorScheme = when (appTheme) {
        SpectreTheme.MIDNIGHT  -> MidnightScheme
        SpectreTheme.PHANTOM   -> PhantomScheme
        SpectreTheme.OBSIDIAN  -> ObsidianScheme
        SpectreTheme.NORD      -> NordScheme
        SpectreTheme.ROSEWOOD  -> RosewoodScheme
        SpectreTheme.SAGE      -> SageScheme
        SpectreTheme.LIGHT     -> dynamicLightColorScheme(context)
    }

    val isLight = appTheme == SpectreTheme.LIGHT
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars     = isLight
                isAppearanceLightNavigationBars = isLight
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = SpectreTypography,
        content     = content
    )
}
