package no.mwmai.pyquest.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import no.mwmai.pyquest.model.Tier
import no.mwmai.pyquest.ui.theme.CodeStyle
import no.mwmai.pyquest.ui.theme.Pal

/**
 * End of a level. Accuracy is first-attempt accuracy, not attempts-until-right,
 * because the second figure only ever goes up and so measures nothing.
 */
@Composable
fun TierClearedScreen(
    tier: Tier,
    session: QuizSession,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accuracy = (session.accuracy * 100).toInt()
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
            Spacer(Modifier.height(28.dp))
            Box(
                modifier = Modifier
                    .size(132.dp)
                    .background(Pal.LimeSoft, CircleShape)
                    .border(2.dp, Pal.Lime, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            "$accuracy",
                            style = MaterialTheme.typography.headlineMedium,
                            color = Pal.Lime,
                        )
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

            Spacer(Modifier.height(24.dp))
            Text(
                "Tier ${tier.tier} cleared",
                style = MaterialTheme.typography.headlineMedium,
                color = Pal.Text,
            )
            if (tier.subtitle.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    tier.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Pal.Muted,
                )
            }

            Spacer(Modifier.height(22.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Stat("XP", "+${session.xpEarned}", Modifier.weight(1f))
                Stat("TIME", session.elapsedLabel, Modifier.weight(1f))
                Stat("STREAK", "${session.progress.streakDays}d", Modifier.weight(1f))
            }

            session.lastBuilt?.let { code ->
                Spacer(Modifier.height(22.dp))
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("YOU WROTE THIS", style = MaterialTheme.typography.labelMedium, color = Pal.Faint)
                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Pal.Ground, RoundedCornerShape(12.dp))
                            .border(1.dp, Pal.Edge, RoundedCornerShape(12.dp))
                            .horizontalScroll(rememberScrollState())
                            .padding(14.dp),
                    ) {
                        Text(code, style = CodeStyle, color = Pal.Lime, softWrap = false)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }

        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 14.dp)) {
            Button(
                onClick = onBack,
                shape = RoundedCornerShape(13.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Pal.Lime,
                    contentColor = Pal.Screen,
                ),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text(
                    "Claim ${session.xpEarned} XP",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
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
        Text(value, style = MaterialTheme.typography.titleLarge, color = Pal.Text)
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = Pal.Faint)
    }
}
