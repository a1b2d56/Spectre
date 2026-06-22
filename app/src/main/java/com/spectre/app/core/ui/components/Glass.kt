package com.spectre.app.core.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.vibrancy

/**
 * A reusable modifier that applies frosted glass effects on supported API levels,
 * and falls back to a solid color on older devices.
 *
 * - API 31+ (Android 12+): Uses the backdrop library's `drawBackdrop` to render
 *   real-time blur and vibrancy effects on the captured background layer.
 * - API 24–30 (Android 7–11): Falls back to a solid [fallbackColor] background
 *   to prevent frame drops from software-based rendering.
 *
 * @param backdrop      The [Backdrop] instance created via `rememberLayerBackdrop()`.
 * @param shape         The clipping shape for the glass panel (e.g. CircleShape, RoundedCornerShape).
 * @param blurRadius    The blur radius in dp. Higher values produce a stronger frosted effect.
 * @param tintColor     A translucent color overlay to apply on top of the frosted glass (e.g., Color.White.copy(alpha=0.4f)).
 * @param fallbackColor Solid color used on pre-API-31 devices.
 * @param borderColor   Optional border color for a thin highlight ring.
 * @param borderWidth   Width of the highlight border.
 */
@Composable
fun Modifier.glassPanel(
    backdrop: Backdrop,
    shape: Shape = RoundedCornerShape(18.dp),
    blurRadius: Dp = 16.dp,
    tintColor: Color = Color.White.copy(alpha = 0.4f),
    fallbackColor: Color = Color(0xFFD2E2F2),
    borderColor: Color = Color.White.copy(alpha = 0.18f),
    borderWidth: Dp = 0.5.dp
): Modifier {
    val density = LocalDensity.current
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        // API 31+ : real backdrop blur + translucent tint overlay
        this
            .drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    vibrancy()
                    blur(with(density) { blurRadius.toPx() })
                }
            )
            .background(tintColor, shape)
            .border(borderWidth, borderColor, shape)
    } else {
        // Pre-Android 12 : solid fallback
        this
            .background(fallbackColor, shape)
            .border(borderWidth, borderColor, shape)
    }
}
