package no.mwmai.pyquest.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import no.mwmai.pyquest.model.Question
import no.mwmai.pyquest.model.QuestionType
import no.mwmai.pyquest.model.Tier
import no.mwmai.pyquest.ui.theme.CodeStyle
import no.mwmai.pyquest.ui.theme.Pal

/**
 * The one screen that matters.
 *
 * The layout is three fixed bands rather than one long scroll: a header that
 * never moves, a middle that scrolls, and a footer holding the verdict and the
 * single button. That is what keeps the game inside a 412 x 892 phone, because
 * the button a player needs is never below the fold no matter how long the code
 * or the explanation runs.
 */
@Composable
fun QuestionScreen(
    tier: Tier,
    session: QuizSession,
    plainLabels: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val question = session.current
    if (question == null) {
        TierClearedScreen(tier = tier, session = session, onBack = onBack, modifier = modifier)
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Pal.Screen)
            .windowInsetsPadding(WindowInsets.systemBars),
    ) {
        QuestionHeader(
            tierLabel = "TIER ${tier.tier} · ${tier.title.uppercase()}",
            done = session.total - session.remaining + 1,
            total = session.total,
            onClose = onBack,
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp),
        ) {
            Text(
                text = question.prompt,
                style = MaterialTheme.typography.headlineSmall,
                color = Pal.Text,
            )

            question.code?.let { snippet ->
                Spacer(Modifier.height(14.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Pal.Ground, RoundedCornerShape(12.dp))
                        .horizontalScroll(rememberScrollState())
                        .padding(14.dp),
                ) {
                    Text(text = snippet, style = CodeStyle, color = Pal.Text, softWrap = false)
                }
            }

            Spacer(Modifier.height(18.dp))

            when (question.type) {
                QuestionType.MCQ -> McqAnswer(
                    options = question.options,
                    selected = session.selection.firstOrNull(),
                    correctIndex = question.options.indexOf(question.answer.firstOrNull() ?: ""),
                    checked = session.checked,
                    onSelect = session::select,
                )

                QuestionType.BLOCKS, QuestionType.ORDER -> BlocksAnswer(
                    choices = question.tray,
                    placed = session.selection,
                    onPlacedChange = session::setBlocks,
                    enabled = !session.checked,
                )

                QuestionType.FILL -> FillAnswer(
                    question = question,
                    placed = session.slots,
                    onPlacedChange = session::placeInSlots,
                    plainLabels = plainLabels,
                    enabled = !session.checked,
                    checked = session.checked,
                )

                QuestionType.PIPELINE -> PipelineAnswer(
                    question = question,
                    placed = session.slots,
                    onPlacedChange = session::placeInSlots,
                    plainLabels = plainLabels,
                    enabled = !session.checked,
                )
            }

            Spacer(Modifier.height(24.dp))
        }

        QuestionFooter(
            checked = session.checked,
            correct = session.lastCorrect,
            explain = question.explain,
            enabled = session.checked || session.canSubmit,
            onClick = { if (session.checked) session.next() else session.submit() },
        )
    }
}

@Composable
private fun QuestionHeader(
    tierLabel: String,
    done: Int,
    total: Int,
    onClose: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(Pal.Chip, CircleShape)
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) {
                Text("×", style = MaterialTheme.typography.titleLarge, color = Pal.Faint)
            }
            Spacer(Modifier.width(12.dp))
            // A plain "4 / 5" beats a progress bar here: it says how much is left
            // in a form a player can hold in their head.
            Text(
                text = "$done / $total",
                style = CodeStyle,
                color = Pal.Lime,
                modifier = Modifier
                    .background(Pal.LimeSoft, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
            Spacer(Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Text(tierLabel, style = MaterialTheme.typography.labelMedium, color = Pal.Faint)
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun QuestionFooter(
    checked: Boolean,
    correct: Boolean,
    explain: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Pal.Screen)
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        if (checked) {
            val tint = if (correct) Pal.Lime else Pal.Coral
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (correct) Pal.LimeSoft else Pal.CoralSoft, RoundedCornerShape(12.dp))
                    .border(1.dp, tint.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .padding(13.dp),
            ) {
                Text(
                    text = if (correct) "Correct" else "Not quite",
                    style = MaterialTheme.typography.titleSmall,
                    color = tint,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(5.dp))
                Text(explain, style = MaterialTheme.typography.bodyMedium, color = Pal.Muted)
            }
            Spacer(Modifier.height(12.dp))
        }
        Button(
            onClick = onClick,
            enabled = enabled,
            shape = RoundedCornerShape(13.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Pal.Lime,
                contentColor = Pal.Screen,
                disabledContainerColor = Pal.Chip,
                disabledContentColor = Pal.Locked,
            ),
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Text(
                if (checked) "Continue" else "Check",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
