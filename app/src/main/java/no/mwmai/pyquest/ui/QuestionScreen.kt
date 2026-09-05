package no.mwmai.pyquest.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
 *
 * Pytor sits in the header. Before the check he hands out hints; after it he
 * has the expert note. Both live in a bottom sheet so the footer stays the
 * same height whatever he has to say.
 */
@Composable
fun QuestionScreen(
    tier: Tier,
    level: Int,
    session: QuizSession,
    plainLabels: Boolean,
    onBack: () -> Unit,
    onReview: () -> Unit,
    onAskPytor: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val question = session.current
    if (question == null) {
        LevelClearedScreen(
            tier = tier,
            level = level,
            session = session,
            onClaim = onBack,
            onReview = onReview,
            modifier = modifier,
        )
        return
    }

    var sheetOpen by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Pal.Screen)
            .windowInsetsPadding(WindowInsets.systemBars),
    ) {
        QuestionHeader(
            tierLabel = if (session.isReview) {
                "REVIEW · TIER ${tier.tier}"
            } else {
                "TIER ${tier.tier} · LEVEL $level · ${tier.title.uppercase()}"
            },
            solved = session.solved,
            total = session.total,
            hintsAvailable = question.hints.size - session.hintsShown,
            checked = session.checked,
            onClose = onBack,
            onPytor = { sheetOpen = true },
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
                CodeBlock(snippet)
            }

            Spacer(Modifier.height(18.dp))

            when (question.type) {
                QuestionType.MCQ -> McqAnswer(
                    options = session.displayedOptions,
                    selected = session.selectedDisplayIndex,
                    correctIndex = session.correctDisplayIndex,
                    checked = session.checked,
                    onSelect = session::selectDisplayed,
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
            hasNote = !question.deep.isNullOrBlank(),
            enabled = session.checked || session.canSubmit,
            onClick = { if (session.checked) session.next() else session.submit() },
            onNote = { sheetOpen = true },
        )
    }

    if (sheetOpen) {
        PytorSheet(
            question = question,
            session = session,
            onDismiss = { sheetOpen = false },
            onOpenChat = {
                sheetOpen = false
                onAskPytor()
            },
        )
    }
}

@Composable
private fun QuestionHeader(
    tierLabel: String,
    solved: Int,
    total: Int,
    hintsAvailable: Int,
    checked: Boolean,
    onClose: () -> Unit,
    onPytor: () -> Unit,
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
            // Distinct questions solved. Never goes backwards, even after a miss.
            Text(
                text = "$solved / $total",
                style = CodeStyle,
                color = Pal.Lime,
                modifier = Modifier
                    .background(Pal.LimeSoft, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
            Spacer(Modifier.weight(1f))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(Pal.Card, RoundedCornerShape(20.dp))
                    .border(1.dp, Pal.LimeEdge, RoundedCornerShape(20.dp))
                    .clickable(onClick = onPytor)
                    .padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
            ) {
                PytorAvatar(size = 26.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    when {
                        checked -> "Pytor's note"
                        hintsAvailable > 0 -> "Ask Pytor"
                        else -> "Pytor"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = Pal.Lime,
                )
            }
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
    hasNote: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    onNote: () -> Unit,
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
                Text(
                    inlineCode(explain),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Pal.Muted,
                    maxLines = 4,
                )
                if (hasNote) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable(onClick = onNote),
                    ) {
                        PytorAvatar(size = 20.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Read Pytor's note",
                            style = MaterialTheme.typography.labelLarge,
                            color = Pal.Lime,
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        PrimaryButton(
            text = if (checked) "Continue" else "Check",
            onClick = onClick,
            enabled = enabled,
        )
    }
}
