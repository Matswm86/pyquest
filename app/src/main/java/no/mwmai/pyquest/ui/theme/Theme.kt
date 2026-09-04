package no.mwmai.pyquest.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Tokens lifted from the Pythonic design canvas. The app is dark only: the whole
 * palette is built around lime on near-black, and a light variant would need its
 * own set of accent values rather than an inversion of these.
 */
object Pal {
    val Ground = Color(0xFF07090C)
    val Screen = Color(0xFF0C0F14)
    val Card = Color(0xFF11161D)
    val CardOpen = Color(0xFF151C25)
    val Chip = Color(0xFF1D242E)
    val ChipDim = Color(0xFF171E27)

    val Lime = Color(0xFFB8F04A)
    val LimeSoft = Color(0x24B8F04A)
    val LimeEdge = Color(0x59B8F04A)
    val Coral = Color(0xFFFF6B5A)
    val CoralSoft = Color(0x26FF6B5A)
    val Violet = Color(0xFFA98BFF)

    val Text = Color(0xFFECEFF4)
    val Muted = Color(0xFF98A2B2)
    val Faint = Color(0xFF8B95A5)
    val Locked = Color(0xFF5D6675)
    val Hairline = Color(0x12FFFFFF)
    val Edge = Color(0x17FFFFFF)

    // Block kinds, so a player learns to read "this slot wants an expression"
    // from colour before they can read it from syntax.
    val VarFill = Color(0x216AA9FF)
    val VarEdge = Color(0x736AA9FF)
    val VarText = Color(0xFF8FC0FF)
    val ExprFill = Color(0x21FFB84D)
    val ExprEdge = Color(0x73FFB84D)
    val ExprText = Color(0xFFFFC876)
    val CallFill = Color(0x21A98BFF)
    val CallEdge = Color(0x73A98BFF)
    val CallText = Color(0xFFBFA7FF)
}

private val PyQuestColors = darkColorScheme(
    primary = Pal.Lime,
    onPrimary = Pal.Screen,
    secondary = Pal.Violet,
    onSecondary = Pal.Screen,
    background = Pal.Screen,
    onBackground = Pal.Text,
    surface = Pal.Card,
    onSurface = Pal.Text,
    surfaceVariant = Pal.Chip,
    onSurfaceVariant = Pal.Faint,
    outline = Pal.Edge,
    error = Pal.Coral,
    onError = Pal.Screen,
)

/** True while the player is answering, so shared widgets can dim themselves. */
val LocalAnswering = staticCompositionLocalOf { true }

@Composable
fun PyQuestTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalAnswering provides true) {
        MaterialTheme(
            colorScheme = PyQuestColors,
            typography = PyQuestTypography,
            content = content,
        )
    }
}
