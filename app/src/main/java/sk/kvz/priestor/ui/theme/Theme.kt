package sk.kvz.priestor.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Dočasná paleta – prispôsobíme podľa vzoru dizajnu.
private val Light = lightColorScheme(
    primary = Color(0xFF2F6B4F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDE8D6),
    onPrimaryContainer = Color(0xFF0D2A1C),
    secondary = Color(0xFF5B6A60),
    secondaryContainer = Color(0xFFDDE6DE),
    background = Color(0xFFF4F1EA),
    surface = Color(0xFFF4F1EA),
    surfaceContainer = Color(0xFFECE8DF),
    surfaceContainerLow = Color(0xFFF0EDE5),
    surfaceContainerHigh = Color(0xFFE6E2D8),
    error = Color(0xFFB3261E),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF9DD3B4),
    onPrimary = Color(0xFF00391F),
    primaryContainer = Color(0xFF1A5136),
    onPrimaryContainer = Color(0xFFCDE8D6),
    background = Color(0xFF141714),
    surface = Color(0xFF141714),
    surfaceContainer = Color(0xFF1E221E),
    surfaceContainerLow = Color(0xFF191C19),
    surfaceContainerHigh = Color(0xFF282C28),
)

@Composable
fun PriestorTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        content = content,
    )
}
