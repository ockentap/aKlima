package com.example.aklima

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val scheme = darkColorScheme(
    primary = Color(0xFF7FC4FF),
    onPrimary = Color(0xFF00243D),
    primaryContainer = Color(0xFF0B3D5C),
    onPrimaryContainer = Color(0xFFCFE7FF),
    secondary = Color(0xFFFFB74D),
    onSecondary = Color(0xFF3A2200),
    background = Color(0xFF0B1520),
    onBackground = Color(0xFFE6EEF6),
    surface = Color(0xFF111C28),
    onSurface = Color(0xFFE6EEF6),
    surfaceVariant = Color(0xFF1B2A38),
    onSurfaceVariant = Color(0xFFB6C4D2),
    error = Color(0xFFFF8A80),
)

@Composable
fun AKlimaTheme(content: @Composable () -> Unit) {
    // aKlima ships dark only — it is a night-stand / wall-panel appliance remote
    MaterialTheme(colorScheme = scheme, content = content)
}
