package com.techperbyte.tools.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary            = Primary,
    onPrimary          = Color.White,
    primaryContainer   = Color(0xFF1F3A5F),
    secondary          = Accent,
    onSecondary        = Color.White,
    background         = Background,
    onBackground       = TextPrimary,
    surface            = Surface,
    onSurface          = TextPrimary,
    surfaceVariant     = SurfaceVariant,
    onSurfaceVariant   = TextSecondary,
    outline            = Border,
    error              = Danger,
    onError            = Color.White,
)

@Composable
fun TechPerByteTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography  = Typography,
        content     = content,
    )
}
