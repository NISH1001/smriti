package com.nishparadox.smriti.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

enum class ThemeMode { SYSTEM, DARK, LIGHT;
    companion object {
        fun from(s: String) = runCatching { valueOf(s.uppercase()) }.getOrDefault(DARK)
    }
}

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8AB4F8),
    onPrimary = Color(0xFF0B1220),
    background = Color(0xFF0E1320),
    onBackground = Color(0xFFE7EAF0),
    surface = Color(0xFF161D2C),
    onSurface = Color(0xFFE7EAF0),
    surfaceVariant = Color(0xFF223049),
    onSurfaceVariant = Color(0xFFB9C4D6),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF2A4B8D),
    background = Color(0xFFF6F7F9),
    surface = Color(0xFFFFFFFF),
)

@Composable
fun SmritiTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, content = content)
}
