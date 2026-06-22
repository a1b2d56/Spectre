package com.spectre.app.core.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// ── Semantic / Status (From Spectre) ──
val StrengthWeak      = Color(0xFFEF4444)
val StrengthFair      = Color(0xFFF97316)
val StrengthGood      = Color(0xFFEAB308)
val StrengthStrong    = Color(0xFF22C55E)
val StrengthExcellent = Color(0xFF06B6D4)

val DangerRed    = Color(0xFFEF4444)
val WarningAmber = Color(0xFFF59E0B)
val SafeGreen    = Color(0xFF22C55E)
val InfoBlue     = Color(0xFF3B82F6)

// Chart palette (From Spectre)
val chartColors = listOf(
    Color(0xFFBDA67A),  // tan
    Color(0xFF7BA7BC),  // steel blue
    Color(0xFF9B8EC4),  // soft purple
    Color(0xFF7DB87D),  // sage green
    Color(0xFFBC7B8B),  // dusty rose
    Color(0xFFBC9B6A),  // warm amber
    Color(0xFF7BC4B8),  // teal
    Color(0xFFB87BAA),  // mauve
)

// ── Midnight (AMOLED black — From Ex-Employee) ──
val MidnightColorScheme = darkColorScheme(
    primary = Color(0xFF81D4FA),
    onPrimary = Color(0xFF003549),
    primaryContainer = Color(0xFF004D69),
    onPrimaryContainer = Color(0xFFB3E5FC),
    secondary = Color(0xFF4FC3F7),
    onSecondary = Color(0xFF002538),
    surface = Color(0xFF000000),
    onSurface = Color(0xFFF4F4F5),
    surfaceVariant = Color(0xFF0A0A0A),
    error = Color(0xFFFFB4AB),
    background = Color(0xFF000000),
    onBackground = Color(0xFFF4F4F5),
    surfaceContainer = Color(0xFF050505),
    outlineVariant = Color(0xFF18181B),
)

// ── Phantom (Luxury Deep Violet / Amethyst — From Ex-Employee) ──
val PhantomColorScheme = darkColorScheme(
    primary = Color(0xFFD6BCFA),
    onPrimary = Color(0xFF3B0764),
    primaryContainer = Color(0xFF553C9A),
    onPrimaryContainer = Color(0xFFEDE9FE),
    secondary = Color(0xFFB794F4),
    onSecondary = Color(0xFF2D3748),
    surface = Color(0xFF161524),
    onSurface = Color(0xFFE2E0E7),
    surfaceVariant = Color(0xFF28263C),
    error = Color(0xFFEF4444),
    background = Color(0xFF0F0E17),
    onBackground = Color(0xFFE2E0E7),
)

// ── Obsidian (Sleek Charcoal & Cyan Tech — From Ex-Employee) ──
val ObsidianColorScheme = darkColorScheme(
    primary = Color(0xFF00E5FF),
    onPrimary = Color(0xFF00363D),
    primaryContainer = Color(0xFF004E57),
    onPrimaryContainer = Color(0xFFB2F8FF),
    secondary = Color(0xFF22D3EE),
    onSecondary = Color(0xFF0F172A),
    surface = Color(0xFF11171D),
    onSurface = Color(0xFFECEFF1),
    surfaceVariant = Color(0xFF202B36),
    error = Color(0xFFEF4444),
    background = Color(0xFF0A0D10),
    onBackground = Color(0xFFECEFF1),
)

// ── Espresso (Warm Rich Cocoa & Cream Gold — From Ex-Employee) ──
val EspressoColorScheme = darkColorScheme(
    primary = Color(0xFFE5C49F),
    onPrimary = Color(0xFF3D2612),
    primaryContainer = Color(0xFF5C3E21),
    onPrimaryContainer = Color(0xFFFBEFE3),
    secondary = Color(0xFFBDA67A),
    onSecondary = Color(0xFF231F1E),
    surface = Color(0xFF1E1714),
    onSurface = Color(0xFFECE2DB),
    surfaceVariant = Color(0xFF2E2420),
    error = Color(0xFFEF4444),
    background = Color(0xFF140F0D),
    onBackground = Color(0xFFECE2DB),
)

// ── Matcha (Organic Soothing Sage & Green Tea — From Ex-Employee) ──
val MatchaColorScheme = darkColorScheme(
    primary = Color(0xFFA7D7C5),
    onPrimary = Color(0xFF1B3B2B),
    primaryContainer = Color(0xFF2D5C43),
    onPrimaryContainer = Color(0xFFE8F5E9),
    secondary = Color(0xFF8FBC8F),
    onSecondary = Color(0xFF141C14),
    surface = Color(0xFF121A15),
    onSurface = Color(0xFFE1E8E4),
    surfaceVariant = Color(0xFF223027),
    error = Color(0xFFEF4444),
    background = Color(0xFF0B100D),
    onBackground = Color(0xFFE1E8E4),
)

// ── Nord (Arctic Clean Blue-Grey & Ice-Blue — From Ex-Employee) ──
val NordColorScheme = darkColorScheme(
    primary = Color(0xFF88C0D0),
    onPrimary = Color(0xFF2E3440),
    primaryContainer = Color(0xFF434C5E),
    onPrimaryContainer = Color(0xFFD8DEE9),
    secondary = Color(0xFF81A1C1),
    onSecondary = Color(0xFF2E3440),
    surface = Color(0xFF3B4252),
    onSurface = Color(0xFFECEFF4),
    surfaceVariant = Color(0xFF4C566A),
    error = Color(0xFFEF4444),
    background = Color(0xFF2E3440),
    onBackground = Color(0xFFECEFF4),
)

// ── Rose (Premium Blush Quartz & Mauve — From Ex-Employee) ──
val RoseColorScheme = darkColorScheme(
    primary = Color(0xFFF3C5D6),
    onPrimary = Color(0xFF4A1E2F),
    primaryContainer = Color(0xFF6E3249),
    onPrimaryContainer = Color(0xFFFFECF2),
    secondary = Color(0xFFE8A0BF),
    onSecondary = Color(0xFF1C1418),
    surface = Color(0xFF1A1417),
    onSurface = Color(0xFFEAE2E4),
    surfaceVariant = Color(0xFF2A2025),
    error = Color(0xFFEF4444),
    background = Color(0xFF120E10),
    onBackground = Color(0xFFEAE2E4),
)

// ── Fallback schemes (From Ex-Employee) ──
val FallbackSkyBlueLightScheme = lightColorScheme(
    primary = Color(0xFF0288D1),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB3E5FC),
    onPrimaryContainer = Color(0xFF01579B),
    secondary = Color(0xFF0277BD),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1C1E),
)

val FallbackSkyBlueDarkScheme = darkColorScheme(
    primary = Color(0xFF81D4FA),
    onPrimary = Color(0xFF003549),
    primaryContainer = Color(0xFF004D69),
    onPrimaryContainer = Color(0xFFB3E5FC),
    secondary = Color(0xFF4FC3F7),
    surface = Color(0xFF212B36),
    onSurface = Color(0xFFE2E2E6),
)
