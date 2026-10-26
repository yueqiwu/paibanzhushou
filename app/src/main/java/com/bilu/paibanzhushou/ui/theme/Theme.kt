package com.bilu.paibanzhushou.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF4A90E2),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE3EEFB),
    onPrimaryContainer = Color(0xFF0B1020),
    secondary = Color(0xFF6AC695),
    onSecondary = Color.White,
    tertiary = Color(0xFF9C27B0),
    background = Color(0xFFF3FFFB),
    onBackground = Color(0xFF0B1020),
    surface = Color.White,
    onSurface = Color(0xFF0B1020),
    surfaceVariant = Color(0xFFF5F7FB),
    onSurfaceVariant = Color(0xFF5B6B7F),
    outline = Color(0xFF9AA3B2)
)

@Composable
fun PaibanzhushouTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = Typography,
        content = content
    )
}
