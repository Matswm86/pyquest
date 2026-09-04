package no.mwmai.pyquest.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import no.mwmai.pyquest.ui.theme.CodeStyle
import no.mwmai.pyquest.ui.theme.Pal

/**
 * Four options, tap one. Each carries a letter key so a player can talk about
 * "B" rather than re-reading the text, and after checking the right row turns
 * lime while a wrong pick turns coral, showing both what was chosen and what was
 * true without leaving the screen.
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
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        options.forEachIndexed { index, option ->
            val picked = selected == index
            val right = checked && index == correctIndex
            val wrong = checked && picked && index != correctIndex

            val edge = when {
                right -> Pal.Lime
                wrong -> Pal.Coral
                picked -> Pal.Lime
                else -> Pal.Hairline
            }
            val fill = when {
                right -> Pal.LimeSoft
                wrong -> Pal.CoralSoft
                else -> Pal.Card
            }
            val ink = when {
                right -> Pal.Lime
                wrong -> Pal.Coral
                else -> Pal.Text
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(fill, RoundedCornerShape(12.dp))
                    .border(if (picked || checked) 1.5.dp else 1.dp, edge, RoundedCornerShape(12.dp))
                    .clickable(enabled = !checked) { onSelect(index) }
                    .padding(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .background(
                            if (right || (picked && !checked)) Pal.Lime else if (wrong) Pal.Coral else Pal.Chip,
                            RoundedCornerShape(8.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = ('A' + index).toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (right || wrong || (picked && !checked)) Pal.Screen else Pal.Faint,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = option,
                    style = if (option.looksLikeCode()) CodeStyle else MaterialTheme.typography.bodyLarge,
                    color = ink,
                )
            }
        }
    }
}

/** Monospace the option when it is Python rather than prose. */
private fun String.looksLikeCode(): Boolean =
    length < 34 && (any { it in "()[]{}=<>_" } || startsWith("print") || contains("def ") || contains("import "))
