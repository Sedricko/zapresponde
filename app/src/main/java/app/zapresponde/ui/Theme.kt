package app.zapresponde.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val Green = Color(0xFF128C5E)
val GreenLight = Color(0xFF25D07A)

private val Light = lightColorScheme(
    primary = Green, onPrimary = Color.White,
    primaryContainer = Color(0xFFD3F5E3), onPrimaryContainer = Color(0xFF00391F),
    secondary = Color(0xFF34675A), secondaryContainer = Color(0xFFE3EFEA),
    background = Color(0xFFF6F8F7), surface = Color(0xFFF6F8F7),
    surfaceContainer = Color.White, surfaceContainerLow = Color.White, surfaceContainerHigh = Color(0xFFEDF2EF),
    error = Color(0xFFC62828), errorContainer = Color(0xFFFDE7E7),
)

private val Dark = darkColorScheme(
    primary = GreenLight, onPrimary = Color(0xFF003919),
    primaryContainer = Color(0xFF0E4D33), onPrimaryContainer = Color(0xFFB9F2D2),
    secondary = Color(0xFF9CCFBF), secondaryContainer = Color(0xFF223630),
    background = Color(0xFF0F1513), surface = Color(0xFF0F1513),
    surfaceContainer = Color(0xFF1A221F), surfaceContainerLow = Color(0xFF1A221F), surfaceContainerHigh = Color(0xFF24302B),
)

@Composable
fun ZapTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        shapes = Shapes(medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(24.dp)),
        content = content,
    )
}
