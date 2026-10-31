package com.viyo.app.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val ViyoViolet = Color(0xFF7C3AED)
val ViyoVioletLight = Color(0xFFA78BFA)
val ViyoBlue = Color(0xFF3B82F6)
val ViyoDarkBg = Color(0xFF0B0F19)
val ViyoDarkSurface = Color(0xFF111827)
val ViyoDarkCard = Color(0xFF1F2937)

private val DarkColorScheme = darkColorScheme(
    primary = ViyoViolet,
    secondary = ViyoBlue,
    background = ViyoDarkBg,
    surface = ViyoDarkSurface,
    surfaceVariant = ViyoDarkCard,
    onPrimary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White
)

@Composable
fun ViyoTheme(
    darkTheme: Boolean = true, // Default to premium dark theme
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}

