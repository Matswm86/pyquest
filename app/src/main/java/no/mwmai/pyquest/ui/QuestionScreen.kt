package no.mwmai.pyquest.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import no.mwmai.pyquest.model.Question
import no.mwmai.pyquest.model.QuestionType

/**
 * The one screen that matters. Prompt on top, optional code snippet, then either
 * four tap targets or the block tray, then a single button that first checks the
 * answer and then moves on.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuestionScreen(
    tierTitle: String,
    session: QuizSession,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val question = session.current
    if (question == null) {
        LevelCompleteScreen(
            tierTitle = tierTitle,
            correct = session.correct,
            answered = session.answered,
            xp = session.progress.xp,
            onBack = onBack,
            modifier = modifier,
        )
        return
    }

    val scheme = MaterialTheme.colorScheme
    val done = (session.total - session.remaining).coerceAtLeast(0)

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("$tierTitle, level ${question.level}", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${session.progress.xp} XP, ${session.progress.streakDays} day streak",
                            style = MaterialTheme.typography.labelMedium,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = scheme.background),
            )
        },
        bottomBar = {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                if (session.checked) {
                    ExplainCard(correct = session.lastCorrect, explain = question.explain)
                    Spacer(Modifier.height(12.dp))
                }
                Button(
                    onClick = { if (session.checked) session.next() else session.submit() },
                    enabled = session.checked || session.canSubmit,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                ) {
                    Text(if (session.checked) "Continue" else "Check", style = MaterialTheme.typography.titleMedium)
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            LinearProgressIndicator(
                progress = { if (session.total == 0) 0f else done.toFloat() / session.total },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(20.dp))

            Text(
                text = question.prompt,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )

            question.code?.let { snippet ->
                Spacer(Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(scheme.surface, RoundedCornerShape(12.dp))
                        .padding(14.dp)
                        .horizontalScroll(rememberScrollState()),
                ) {
                    Text(
                        text = snippet,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurface,
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

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
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun ExplainCard(correct: Boolean, explain: String) {
    val scheme = MaterialTheme.colorScheme
    val tint = if (correct) scheme.primary else scheme.error
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(tint.copy(alpha = 0.14f), RoundedCornerShape(12.dp))
            .padding(14.dp),
    ) {
        Text(
            text = if (correct) "Correct" else "Not quite",
            style = MaterialTheme.typography.titleSmall,
            color = tint,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(6.dp))
        Text(text = explain, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurface)
    }
}

@Composable
private fun LevelCompleteScreen(
    tierTitle: String,
    correct: Int,
    answered: Int,
    xp: Int,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Level clear", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(tierTitle, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            StatBlock("First try", "$correct of $answered")
            StatBlock("Total XP", "$xp")
        }
        Spacer(Modifier.height(32.dp))
        Button(onClick = onBack, modifier = Modifier.height(52.dp)) {
            Text("Back to the map")
        }
    }
}

@Composable
private fun StatBlock(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
