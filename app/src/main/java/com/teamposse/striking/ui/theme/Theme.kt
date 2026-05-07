package com.teamposse.striking.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val PosseDarkScheme = darkColorScheme(
    primary = Blood,
    onPrimary = Bone,
    secondary = Bone,
    onSecondary = Ink,
    background = Ink,
    onBackground = Bone,
    surface = Canvas,
    onSurface = Bone,
    surfaceVariant = Ash,
    onSurfaceVariant = Mist,
    outline = Ash,
)

@Composable
fun TeamPosseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = PosseDarkScheme,
        typography = AppTypography,
        content = content
    )
}
