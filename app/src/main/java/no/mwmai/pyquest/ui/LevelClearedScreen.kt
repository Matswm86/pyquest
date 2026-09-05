package no.mwmai.pyquest.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import no.mwmai.pyquest.data.Progress
import no.mwmai.pyquest.model.Tier
import no.mwmai.pyquest.pytor.PytorCoach
import no.mwmai.pyquest.ui.theme.Pal

/**
 * End of a level. Accuracy is first-attempt accuracy, not attempts-until-right,
 * because the second figure only ever goes up and so measures nothing.
 *
 * The first build titled this "Tier N cleared" after every level, which was
 * simply wrong. It now says what happened: the level, or the whole tier when
 * every question in it has reached the mastered box.
 */
@Composable
fun LevelClearedScreen(
    tier: Tier,
    level: Int,
    session: QuizSession,
    onClaim: () -> Unit,
    onReview: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accuracy = (session.accuracy * 100).toInt()
    val today = Progress.today()
    val tierMastered = session.progress.masteryOf(tier.questions.map { it.id }) >= 1f
    val title = when {
        session.isReview -> "Misses reviewed"
        tierMastered -> "Tier ${tier.tier} mastered"
        else -> "Level $level cleared"
    }
    val misses = session.misses

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Pal.Screen)
            .windowInsetsPadding(WindowInsets.systemBars),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(24.dp))
            Box(
                modifier = Modifier
                    .size(132.dp)
                    .background(Pal.LimeSoft, CircleShape)
                    .border(2.dp, Pal.Lime, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("$accuracy", style = MaterialTheme.typography.headlineMedium, color = Pal.Lime)
                        Text(
                            "%",
                            style = MaterialTheme.typography.titleMedium,
                            color = Pal.Lime,
                            modifier = Modifier.padding(bottom = 3.dp),
                        )
                    }
                    Text("ACCURACY", style = MaterialTheme.typography.labelSmall, color = Pal.Faint)
                }
            }

            Spacer(Modifier.height(22.dp))
            Text(title, style = MaterialTheme.typography.headlineMedium, color = Pal.Text)
            Spacer(Modifier.height(6.dp))
            Text(
                "Tier ${tier.tier} · ${tier.title}",
                style = MaterialTheme.typography.bodyMedium,
                color = Pal.Muted,
            )

            Spacer(Modifier.height(20.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Stat("XP", "+${session.xpEarned}", Modifier.weight(1f))
                Stat("TIME", session.elapsedLabel, Modifier.weight(1f))
                Stat("HINTS", "${session.hintsUsed}", Modifier.weight(1f))
                Stat("STREAK", "${session.progress.streakOn(today)}d", Modifier.weight(1f))
            }

            Spacer(Modifier.height(20.dp))
            PytorSays(PytorCoach.verdict(session.accuracy, session.hintsUsed, session.isReview))

            session.lastBuilt?.let { code ->
                Spacer(Modifier.height(20.dp))
                Column(modifier = Modifier.fillMaxWidth()) {
                    SectionLabel("YOU WROTE THIS")
                    Spacer(Modifier.height(8.dp))
                    CodeBlock(code, color = Pal.Lime)
                }
            }

            Spacer(Modifier.height(24.dp))
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (misses.isNotEmpty()) {
                SecondaryButton(
                    text = "Review ${misses.size} ${if (misses.size == 1) "miss" else "misses"}",
                    onClick = onReview,
                )
            }
            PrimaryButton(text = "Claim ${session.xpEarned} XP", onClick = onClaim)
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(Pal.Card, RoundedCornerShape(12.dp))
            .border(1.dp, Pal.Edge, RoundedCornerShape(12.dp))
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, style = MaterialTheme.typography.titleMedium, color = Pal.Text)
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = Pal.Faint)
    }
}
