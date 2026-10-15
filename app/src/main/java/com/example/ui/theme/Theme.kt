package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val CameraColorScheme = darkColorScheme(
    primary = TextPrimary,
    onPrimary = MatteBlack,
    secondary = TextSecondary,
    onSecondary = TextPrimary,
    tertiary = AccentStabilizer,
    onTertiary = MatteBlack,
    background = MatteBlack,
    onBackground = TextPrimary,
    surface = CameraSurface,
    onSurface = TextPrimary,
    surfaceVariant = CameraSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = CameraBorder,
    error = AccentRecording,
    onError = Color.White
)

@Composable
fun CineMotionTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = CameraColorScheme,
        typography = Typography,
        content = content
    )
}
