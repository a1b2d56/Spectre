package com.spectre.app.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Builds a [Typography] with larger-than-default sizes for readability.
 * Android's system font-scale is applied on top automatically.
 */
fun appTypography(isBold: Boolean = false, fontFamily: FontFamily? = null): Typography {
    val bodyWeight = if (isBold) FontWeight.Bold else FontWeight.Normal
    val titleWeight = if (isBold) FontWeight.Bold else FontWeight.Medium
    val labelWeight = if (isBold) FontWeight.Bold else FontWeight.Medium

    return Typography(
        displayLarge = TextStyle(fontSize = 57.sp, fontWeight = FontWeight.Normal, fontFamily = fontFamily),
        displayMedium = TextStyle(fontSize = 45.sp, fontWeight = FontWeight.Normal, fontFamily = fontFamily),
        displaySmall = TextStyle(fontSize = 36.sp, fontWeight = FontWeight.Normal, fontFamily = fontFamily),

        headlineLarge = TextStyle(fontSize = 32.sp, fontWeight = titleWeight, fontFamily = fontFamily),
        headlineMedium = TextStyle(fontSize = 28.sp, fontWeight = titleWeight, fontFamily = fontFamily),
        headlineSmall = TextStyle(fontSize = 24.sp, fontWeight = titleWeight, fontFamily = fontFamily),

        titleLarge = TextStyle(fontSize = 22.sp, fontWeight = titleWeight, fontFamily = fontFamily),
        titleMedium = TextStyle(fontSize = 18.sp, fontWeight = titleWeight, fontFamily = fontFamily),
        titleSmall = TextStyle(fontSize = 15.sp, fontWeight = labelWeight, fontFamily = fontFamily),

        bodyLarge = TextStyle(fontSize = 17.sp, fontWeight = bodyWeight, lineHeight = 24.sp, fontFamily = fontFamily),
        bodyMedium = TextStyle(fontSize = 15.sp, fontWeight = bodyWeight, lineHeight = 22.sp, fontFamily = fontFamily),
        bodySmall = TextStyle(fontSize = 13.sp, fontWeight = bodyWeight, lineHeight = 18.sp, fontFamily = fontFamily),

        labelLarge = TextStyle(fontSize = 15.sp, fontWeight = labelWeight, fontFamily = fontFamily),
        labelMedium = TextStyle(fontSize = 13.sp, fontWeight = labelWeight, fontFamily = fontFamily),
        labelSmall = TextStyle(fontSize = 11.sp, fontWeight = labelWeight, fontFamily = fontFamily),
    )
}

// Default Typography fallback for static initializers
val SpectreTypography = appTypography()

// Spacing tokens (From Spectre)
val SpacingXS     = 4.dp
val SpacingSmall  = 8.dp
val SpacingMedium = 16.dp
val SpacingLarge  = 24.dp
val SpacingXL     = 32.dp
val SpacingXXL    = 48.dp
