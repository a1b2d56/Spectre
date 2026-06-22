package com.spectre.app.core.ui.theme

import android.app.Activity
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.spectre.app.R
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

enum class SpectreTheme { LIGHT, DARK, MIDNIGHT, PHANTOM, OBSIDIAN, ESPRESSO, MATCHA, NORD, ROSE }

val LocalAppTheme = staticCompositionLocalOf { SpectreTheme.DARK }
val LocalIsBold = staticCompositionLocalOf { false }

val FigtreeFont = FontFamily(
    Font(R.font.figtree_regular, FontWeight.Normal),
    Font(R.font.figtree_medium, FontWeight.Medium),
    Font(R.font.figtree_semibold, FontWeight.SemiBold),
    Font(R.font.figtree_bold, FontWeight.Bold)
)

val OutfitFont = FontFamily(
    Font(R.font.outfit_regular, FontWeight.Normal),
    Font(R.font.outfit_medium, FontWeight.Medium),
    Font(R.font.outfit_semibold, FontWeight.SemiBold),
    Font(R.font.outfit_bold, FontWeight.Bold)
)

// Shapes from Ex-Employee
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

@Composable
fun FontWeight.dynamic(): FontWeight {
    return if (LocalIsBold.current) {
        when (this) {
            FontWeight.Normal -> FontWeight.Bold
            FontWeight.Medium -> FontWeight.Bold
            FontWeight.SemiBold -> FontWeight.Bold
            FontWeight.Bold -> FontWeight.Bold
            else -> this
        }
    } else {
        when (this) {
            FontWeight.SemiBold -> FontWeight.Medium
            FontWeight.Bold -> FontWeight.Medium
            else -> this
        }
    }
}

@Composable
fun SpectreAppTheme(
    appTheme: SpectreTheme = SpectreTheme.DARK,
    fontScale: Float = 1f,
    isBold: Boolean = false,
    fontFamilyKey: String = "default",
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val useDynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val colorScheme = when (appTheme) {
        SpectreTheme.LIGHT -> when {
            useDynamic -> dynamicLightColorScheme(context)
            else -> FallbackSkyBlueLightScheme
        }
        SpectreTheme.DARK -> when {
            useDynamic -> dynamicDarkColorScheme(context)
            else -> FallbackSkyBlueDarkScheme
        }
        SpectreTheme.MIDNIGHT -> {
            val base = if (useDynamic) dynamicDarkColorScheme(context) else MidnightColorScheme
            // Force pure-black surfaces for AMOLED
            base.copy(
                surface = MidnightColorScheme.surface,
                background = MidnightColorScheme.background,
                surfaceVariant = MidnightColorScheme.surfaceVariant,
                surfaceContainer = MidnightColorScheme.surfaceContainer,
                outlineVariant = MidnightColorScheme.outlineVariant,
            )
        }
        SpectreTheme.PHANTOM  -> PhantomColorScheme
        SpectreTheme.OBSIDIAN -> ObsidianColorScheme
        SpectreTheme.ESPRESSO -> EspressoColorScheme
        SpectreTheme.MATCHA   -> MatchaColorScheme
        SpectreTheme.NORD     -> NordColorScheme
        SpectreTheme.ROSE     -> RoseColorScheme
    }

    val isDark = appTheme != SpectreTheme.LIGHT

    // Edge-to-edge system bars (adapted from Ex-Employee side-effect)
    SideEffect {
        val activity = context as? ComponentActivity ?: return@SideEffect
        val bgArgb = colorScheme.background.toArgb()
        activity.enableEdgeToEdge(
            statusBarStyle = if (isDark) {
                SystemBarStyle.dark(bgArgb)
            } else {
                SystemBarStyle.light(bgArgb, bgArgb)
            },
            navigationBarStyle = if (isDark) {
                SystemBarStyle.dark(bgArgb)
            } else {
                SystemBarStyle.light(bgArgb, bgArgb)
            },
        )
    }

    val fontFamily = when (fontFamilyKey) {
        "figtree" -> FigtreeFont
        "outfit" -> OutfitFont
        else -> null
    }

    val typography = appTypography(isBold, fontFamily)
    val currentDensity = androidx.compose.ui.platform.LocalDensity.current
    val customDensity = androidx.compose.runtime.remember(currentDensity, fontScale) {
        androidx.compose.ui.unit.Density(
            density = currentDensity.density,
            fontScale = currentDensity.fontScale * fontScale
        )
    }

    CompositionLocalProvider(
        LocalAppTheme provides appTheme,
        LocalIsBold provides isBold,
        androidx.compose.ui.platform.LocalDensity provides customDensity
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            shapes = AppShapes,
            content = content
        )
    }
}
