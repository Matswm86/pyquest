package no.mwmai.pyquest.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import no.mwmai.pyquest.model.Question
import no.mwmai.pyquest.ui.theme.Pal

/**
 * Pytor's sheet on the question screen.
 *
 * Before the check it hands out hints one at a time, mildest first, and never
 * the answer. After the check it shows the expert note, the part of the
 * explanation a working engineer would want. Both end with a way into the
 * chat for anything the canned material does not cover.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PytorSheet(
    question: Question,
    session: QuizSession,
    onDismiss: () -> Unit,
    onOpenChat: () -> Unit,
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = Pal.Card,
        contentColor = Pal.Text,
        scrimColor = Pal.Ground.copy(alpha = 0.7f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PytorAvatar(size = 44.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Pytor", style = MaterialTheme.typography.titleMedium, color = Pal.Text)
                    Text(
                        if (session.checked) "The expert note" else "Hints, never the answer",
                        style = MaterialTheme.typography.bodySmall,
                        color = Pal.Faint,
                    )
                }
            }
            Spacer(Modifier.height(18.dp))

            if (!session.checked) {
                HintList(question, session)
            } else {
                DeepNote(question, session.lastCorrect)
            }

            Spacer(Modifier.height(18.dp))
            SecondaryButton(
                text = if (session.checked) "Ask Pytor more about this" else "Chat with Pytor about this",
                onClick = onOpenChat,
            )
        }
    }
}

@Composable
private fun HintList(question: Question, session: QuizSession) {
    val shown = session.hintsShown
    if (shown == 0) {
        Text(
            "Stuck? I will nudge you one step at a time. The block you place is still yours.",
            style = MaterialTheme.typography.bodyMedium,
            color = Pal.Muted,
        )
        Spacer(Modifier.height(14.dp))
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        question.hints.take(shown).forEachIndexed { index, hint ->
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    "${index + 1}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Pal.Lime,
                    modifier = Modifier
                        .background(Pal.LimeSoft, RoundedCornerShape(6.dp))
                        .padding(horizontal = 7.dp, vertical = 4.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(inlineCode(hint), style = MaterialTheme.typography.bodyMedium, color = Pal.Text)
            }
        }
    }
    if (shown > 0) Spacer(Modifier.height(14.dp))
    if (shown < question.hints.size) {
        PrimaryButton(
            text = if (shown == 0) "Give me a hint" else "Another hint (${question.hints.size - shown} left)",
            onClick = { session.revealHint() },
        )
    } else {
        Text(
            "That is every hint I have. Place the blocks; being wrong here costs nothing but a rematch.",
            style = MaterialTheme.typography.bodySmall,
            color = Pal.Faint,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DeepNote(question: Question, correct: Boolean) {
    Text(
        if (correct) "Right. Here is why it matters beyond this question." else "Not this time. Here is what was really being asked.",
        style = MaterialTheme.typography.titleSmall,
        color = if (correct) Pal.Lime else Pal.Coral,
        fontWeight = FontWeight.SemiBold,
    )
    Spacer(Modifier.height(10.dp))
    Text(inlineCode(question.explain), style = MaterialTheme.typography.bodyMedium, color = Pal.Muted)
    question.deep?.let { deep ->
        Spacer(Modifier.height(14.dp))
        SectionLabel("PYTOR'S NOTE")
        Spacer(Modifier.height(6.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Pal.Ground, RoundedCornerShape(12.dp))
                .border(1.dp, Pal.LimeEdge, RoundedCornerShape(12.dp))
                .padding(13.dp),
        ) {
            Text(inlineCode(deep), style = MaterialTheme.typography.bodyMedium, color = Pal.Text)
        }
    }
    if (question.tags.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            question.tags.forEach { tag ->
                Text(
                    tag,
                    style = MaterialTheme.typography.labelSmall,
                    color = Pal.Faint,
                    modifier = Modifier
                        .background(Pal.ChipDim, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }
}
