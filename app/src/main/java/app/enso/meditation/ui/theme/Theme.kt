package app.enso.meditation.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

@Composable
fun EnsoTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = lightColorScheme(
        primary = Ink, onPrimary = Paper, secondary = Vermilion,
        background = Paper, onBackground = Ink, surface = Paper,
        onSurface = Ink, onSurfaceVariant = FadedInk, surfaceContainer = Paper,
    ), typography = Typography, content = content)
}
