package com.spectre.app.core.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.shader.isRenderEffectSupported

@Composable
fun rememberBlurBackdrop(enableBlur: Boolean): LayerBackdrop {
    val surfaceColor = MaterialTheme.colorScheme.surface
    return rememberLayerBackdrop {
        if (enableBlur && isRenderEffectSupported()) {
            drawRect(surfaceColor)
            drawContent()
        }
    }
}
