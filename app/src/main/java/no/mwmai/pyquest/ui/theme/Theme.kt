package no.mwmai.pyquest.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Pytor green on near-black, so code blocks read like an editor. */
private val PytorGreen = Color(0xFF4CAF50)
private val PytorGreenDeep = Color(0xFF2E7D32)
private val Ink = Color(0xFF0B1220)
private val InkRaised = Color(0xFF16203A)
private val Amber = Color(0xFFFFB300)

private val DarkColors = darkColorScheme(
    primary = PytorGreen,
    onPrimary = Ink,
    secondary = Amber,
    onSecondary = Ink,
    background = Ink,
    onBackground = Color(0xFFE6EAF2),
    surface = InkRaised,
    onSurface = Color(0xFFE6EAF2),
    surfaceVariant = Color(0xFF1E2A47),
    onSurfaceVariant = Color(0xFFB6C2DA),
    error = Color(0xFFEF5350),
)

private val LightColors = lightColorScheme(
    primary = PytorGreenDeep,
    secondary = Color(0xFFB26A00),
    background = Color(0xFFF7F9FC),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE3E8F0),
    error = Color(0xFFC62828),
)

@Composable
fun PyQuestTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
