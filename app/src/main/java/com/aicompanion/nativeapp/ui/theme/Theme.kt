package com.aicompanion.nativeapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

@Composable
fun AiCompanionTheme(
    themeIndex: Int = 0,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = ThemePresets.getOrElse(themeIndex) { ThemePresets[0] }

    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = colors.primary,
            background = colors.background,
            surface = colors.surface,
            onPrimary = colors.onUserBubble,
            onBackground = colors.textPrimary,
            onSurface = colors.textPrimary,
        )
    } else {
        lightColorScheme(
            primary = colors.primary,
            background = colors.background,
            surface = colors.surface,
            onPrimary = colors.onUserBubble,
            onBackground = colors.textPrimary,
            onSurface = colors.textPrimary,
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
