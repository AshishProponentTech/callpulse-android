package com.proponenttech.callpulse.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = Color(0xFF4A5DF9),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0E5FF),
    onPrimaryContainer = Color(0xFF202C78),
    secondary = Color(0xFF52608D),
    secondaryContainer = Color(0xFFE8ECFF),
    tertiary = Color(0xFF246A55),
    background = Color(0xFFF0F3FF),
    surface = Color(0xFFFAFBFF),
    surfaceVariant = Color(0xFFECF0FF),
    onSurface = Color(0xFF1C2440),
    onSurfaceVariant = Color(0xFF59627D),
    outline = Color(0xFF78829C),
    outlineVariant = Color(0xFFD9DFF3),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFBBC4FF),
    onPrimary = Color(0xFF172582),
    primaryContainer = Color(0xFF283468),
    onPrimaryContainer = Color(0xFFE0E5FF),
    secondary = Color(0xFFBBC5ED),
    secondaryContainer = Color(0xFF303B5C),
    tertiary = Color(0xFF8FD9B4),
    background = Color(0xFF11172A),
    surface = Color(0xFF1A2137),
    surfaceVariant = Color(0xFF242E4C),
    onSurface = Color(0xFFE9EDFF),
    onSurfaceVariant = Color(0xFFB8C1DF),
    outlineVariant = Color(0xFF3C4666),
)

@Composable
fun CallPulseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        shapes = Shapes(
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(18.dp),
            large = RoundedCornerShape(24.dp),
        ),
        content = content,
    )
}
