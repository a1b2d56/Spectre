package com.spectre.app.core.ui.theme

import android.app.Activity
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

enum class SpectreTheme { LIGHT, DARK, MIDNIGHT, PHANTOM, OBSIDIAN, ESPRESSO, MATCHA, NORD, ROSE }

// Phantom (Deep Navy) palette
private val PhantomScheme = darkColorScheme(
    primary              = PhantomPrimary,
    onPrimary            = PhantomOnPrimary,
    primaryContainer     = Color(0xFF3B0764),
    onPrimaryContainer   = Color(0xFFEDE9FE),
    secondary            = PhantomSecondary,
    background           = PhantomBackground,
    surface              = PhantomSurface,
    surfaceVariant       = PhantomSurfaceVar,
    surfaceContainer     = Color(0xFF1C2128),
    surfaceContainerHigh = Color(0xFF2D333B),
    onSurface            = Color(0xFFCDD9E5),
    onBackground         = Color(0xFFCDD9E5),
    onSurfaceVariant     = Color(0xFF8B949E),
    outline              = PhantomOutline,
    error                = DangerRed,
)

// Obsidian (Charcoal + Cyan) palette
private val ObsidianScheme = darkColorScheme(
    primary              = ObsidianPrimary,
    onPrimary            = ObsidianOnPrimary,
    primaryContainer     = Color(0xFF164E63),
    onPrimaryContainer   = Color(0xFFCFFAFE),
    secondary            = ObsidianSecondary,
    background           = Color(0xFF0F172A), // Deeper Slate
    surface              = Color(0xFF1E293B),
    surfaceVariant       = Color(0xFF334155),
    surfaceContainer     = Color(0xFF0F172A), // Slate 900
    surfaceContainerLow  = Color(0xFF020617), // Slate 950
    surfaceContainerHigh = Color(0xFF1E293B), // Slate 800
    onSurface            = Color(0xFFF8FAFC),
    onBackground         = Color(0xFFF8FAFC),
    onSurfaceVariant     = Color(0xFF94A3B8),
    outline              = Color(0xFF334155),
    outlineVariant       = Color(0xFF1E293B),
    error                = DangerRed,
)

// Espresso (Chocolate) palette
private val EspressoScheme = darkColorScheme(
    primary             = EspressoPrimary,
    onPrimary           = EspressoOnPrimary,
    primaryContainer    = Color(0xFF3D2E14),
    onPrimaryContainer  = Color(0xFFF0E0C8),
    secondary           = EspressoSecondary,
    background          = EspressoBackground,
    surface             = EspressoSurface,
    surfaceVariant      = Color(0xFF2B2624),
    surfaceContainer     = Color(0xFF2B2624),
    surfaceContainerHigh = Color(0xFF383230),
    onSurface           = Color(0xFFE8E1DE),
    onBackground        = Color(0xFFE8E1DE),
    onSurfaceVariant    = Color(0xFFADA09A),
    outline             = Color(0xFF5A4F4A),
    error               = DangerRed,
)

// Matcha palette
private val MatchaScheme = darkColorScheme(
    primary             = MatchaPrimary,
    onPrimary           = MatchaOnPrimary,
    primaryContainer    = Color(0xFF2A4A2A),
    onPrimaryContainer  = Color(0xFFD0F0D0),
    secondary           = MatchaSecondary,
    background          = MatchaBackground,
    surface             = MatchaSurface,
    surfaceVariant      = Color(0xFF243224),
    surfaceContainer     = Color(0xFF243224),
    surfaceContainerHigh = Color(0xFF2F3F2F),
    onSurface           = Color(0xFFDAE8DA),
    onBackground        = Color(0xFFDAE8DA),
    onSurfaceVariant    = Color(0xFF98B098),
    outline             = Color(0xFF4A604A),
    error               = DangerRed,
)

// Nord palette
private val NordScheme = darkColorScheme(
    primary             = NordPrimary,
    onPrimary           = NordOnPrimary,
    primaryContainer    = Color(0xFF434C5E),
    onPrimaryContainer  = Color(0xFFD8DEE9),
    secondary           = NordSecondary,
    background          = NordBackground,
    surface             = NordSurface,
    surfaceVariant      = Color(0xFF434C5E),
    surfaceContainer     = Color(0xFF3B4252),
    surfaceContainerHigh = Color(0xFF434C5E),
    onSurface           = Color(0xFFECEFF4),
    onBackground        = Color(0xFFECEFF4),
    onSurfaceVariant    = Color(0xFFD8DEE9),
    outline             = Color(0xFF4C566A),
    error               = DangerRed,
)

// Rosé palette
private val RoseScheme = darkColorScheme(
    primary             = RosePrimary,
    onPrimary           = RoseOnPrimary,
    primaryContainer    = Color(0xFF4A2535),
    onPrimaryContainer  = Color(0xFFF8D8E8),
    secondary           = RoseSecondary,
    background          = RoseBackground,
    surface             = RoseSurface,
    surfaceVariant      = Color(0xFF30222A),
    surfaceContainer     = Color(0xFF30222A),
    surfaceContainerHigh = Color(0xFF3B2A34),
    onSurface           = Color(0xFFE8DDE2),
    onBackground        = Color(0xFFE8DDE2),
    onSurfaceVariant    = Color(0xFFB8A0AC),
    outline             = Color(0xFF5A4050),
    error               = DangerRed,
)

/**
 * Resolves dynamic (Material You) colors where available, falling back to static palettes.
 * The [isDark] parameter is used by LIGHT/DARK/MIDNIGHT to decide which system scheme to use.
 */
private fun dynamicOrFallback(context: android.content.Context, isDark: Boolean): ColorScheme {
    return if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
}

@Composable
fun SpectreTheme(
    appTheme: SpectreTheme = SpectreTheme.DARK,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current

    val colorScheme = when (appTheme) {
        SpectreTheme.LIGHT    -> dynamicOrFallback(context, isDark = false)
        SpectreTheme.DARK     -> dynamicOrFallback(context, isDark = true)
        SpectreTheme.MIDNIGHT -> {
            // Material You colors with pure-black background for AMOLED screens
            val base = dynamicOrFallback(context, isDark = true)
            base.copy(
                background           = Color.Black,
                surface              = Color.Black,
                surfaceVariant       = Color(0xFF080808),
                surfaceContainer     = Color(0xFF040404),
                surfaceContainerLow  = Color(0xFF020202),
                surfaceContainerHigh = Color(0xFF0A0A0A),
                surfaceDim           = Color.Black,
                onSurface            = Color(0xFFF4F4F5),
                onBackground         = Color(0xFFF4F4F5),
                outline              = Color(0xFF18181B),
                outlineVariant       = Color(0xFF09090B),
            )
        }
        SpectreTheme.PHANTOM  -> PhantomScheme
        SpectreTheme.OBSIDIAN -> ObsidianScheme
        SpectreTheme.ESPRESSO -> EspressoScheme
        SpectreTheme.MATCHA   -> MatchaScheme
        SpectreTheme.NORD     -> NordScheme
        SpectreTheme.ROSE     -> RoseScheme
    }

    // Determine whether the status bar icons should be light or dark
    val isLightTheme = appTheme == SpectreTheme.LIGHT

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars     = isLightTheme
                isAppearanceLightNavigationBars = isLightTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = SpectreTypography,
        content     = content
    )
}

