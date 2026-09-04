package no.mwmai.pyquest.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

/**
 * Four options, tap one. After the answer is checked the correct row turns
 * green and a wrong pick turns red, so the player sees both what they chose and
 * what was true without leaving the screen.
 */
@Composable
fun McqAnswer(
    options: List<String>,
    selected: Int?,
    correctIndex: Int,
    checked: Boolean,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        options.forEachIndexed { index, option ->
            val isPicked = selected == index
            val background = when {
                checked && index == correctIndex -> scheme.primary.copy(alpha = 0.22f)
                checked && isPicked -> scheme.error.copy(alpha = 0.22f)
                isPicked -> scheme.primary.copy(alpha = 0.14f)
                else -> scheme.surface
            }
            val outline = when {
                checked && index == correctIndex -> scheme.primary
                checked && isPicked -> scheme.error
                isPicked -> scheme.primary
                else -> scheme.surfaceVariant
            }
            Text(
                text = option,
                style = MaterialTheme.typography.bodyLarge,
                fontFamily = if (option.looksLikeCode()) FontFamily.Monospace else FontFamily.Default,
                color = scheme.onSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(background, RoundedCornerShape(12.dp))
                    .border(if (isPicked || checked) 2.dp else 1.dp, outline, RoundedCornerShape(12.dp))
                    .clickable(enabled = !checked) { onSelect(index) }
                    .padding(horizontal = 14.dp, vertical = 14.dp),
            )
        }
    }
}

/** Monospace the option when it is Python rather than prose. */
private fun String.looksLikeCode(): Boolean =
    any { it in "()[]{}=<>_" } || startsWith("print") || contains("def ") || contains("import ")
