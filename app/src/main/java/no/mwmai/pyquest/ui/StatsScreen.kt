package no.mwmai.pyquest.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import no.mwmai.pyquest.data.Progress
import no.mwmai.pyquest.model.Tier
import no.mwmai.pyquest.ui.theme.Pal

/**
 * The You tab: what the numbers say, what Pytor thinks you should drill, and
 * the three settings the game has. Reset is two taps on purpose.
 */
@Composable
fun StatsScreen(
    progress: Progress,
    tiers: List<Tier>,
    today: String,
    plainLabels: Boolean,
    onTogglePlainLabels: () -> Unit,
    pytorOnline: Boolean,
    onTogglePytorOnline: () -> Unit,
    onAskAbout: (String) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmReset by remember { mutableStateOf(false) }
    val allIds = remember(tiers) { tiers.flatMap { t -> t.questions.map { it.id } } }
    val mastered = allIds.count { progress.box(it) >= Progress.MASTERED_BOX }
    val accuracy = if (progress.answered == 0) 0 else progress.correct * 100 / progress.answered

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Pal.Screen)
            .windowInsetsPadding(WindowInsets.statusBars)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(progress.rank, style = MaterialTheme.typography.headlineMedium, color = Pal.Text)
                Text("${progress.xp} XP", style = MaterialTheme.typography.bodyMedium, color = Pal.Lime)
            }
        }
        progress.nextRank?.let { (name, away) ->
            Spacer(Modifier.height(10.dp))
            val span = (progress.xp + away).toFloat()
            LinearProgressIndicator(
                progress = { if (span == 0f) 0f else progress.xp / span },
                color = Pal.Lime,
                trackColor = Pal.Chip,
                modifier = Modifier.fillMaxWidth().height(6.dp),
            )
            Spacer(Modifier.height(4.dp))
            Text("$away XP to $name", style = MaterialTheme.typography.bodySmall, color = Pal.Faint)
        }

        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            Stat("STREAK", "${progress.streakOn(today)}d", Modifier.weight(1f))
            Stat("ANSWERED", "${progress.answered}", Modifier.weight(1f))
            Stat("ACCURACY", "$accuracy%", Modifier.weight(1f))
            Stat("MASTERED", "$mastered/${allIds.size}", Modifier.weight(1f))
        }

        Spacer(Modifier.height(22.dp))
        SectionLabel("MASTERY BY TIER")
        Spacer(Modifier.height(10.dp))
        tiers.forEach { tier ->
            val m = progress.masteryOf(tier.questions.map { it.id })
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 5.dp)) {
                Text(
                    "${tier.tier}",
                    style = MaterialTheme.typography.labelLarge,
                    color = Pal.Faint,
                    modifier = Modifier.width(20.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(tier.title, style = MaterialTheme.typography.bodyMedium, color = Pal.Text)
                    Spacer(Modifier.height(5.dp))
                    LinearProgressIndicator(
                        progress = { m },
                        color = if (m >= 1f) Pal.Lime else Pal.Lime.copy(alpha = 0.8f),
                        trackColor = Pal.Chip,
                        modifier = Modifier.fillMaxWidth().height(5.dp),
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text("${(m * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = Pal.Faint)
            }
        }

        val weak = progress.weakTags
        Spacer(Modifier.height(22.dp))
        SectionLabel("PYTOR SAYS DRILL THESE")
        Spacer(Modifier.height(10.dp))
        if (weak.isEmpty()) {
            Text(
                if (progress.answered < Progress.WEAK_MIN_SEEN) {
                    "Answer a few questions and I will tell you where you are weakest."
                } else {
                    "Nothing stands out. Keep climbing."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = Pal.Muted,
            )
        } else {
            weak.take(6).forEach { (tag, rate) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .background(Pal.Card, RoundedCornerShape(10.dp))
                        .border(1.dp, Pal.Hairline, RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                ) {
                    Text(tag, style = MaterialTheme.typography.bodyMedium, color = Pal.Text, modifier = Modifier.weight(1f))
                    Text("${(rate * 100).toInt()}% missed", style = MaterialTheme.typography.labelSmall, color = Pal.Coral)
                    Spacer(Modifier.width(10.dp))
                    Chip("Ask Pytor", onClick = { onAskAbout(tag) })
                }
            }
        }

        Spacer(Modifier.height(22.dp))
        SectionLabel("SETTINGS")
        Spacer(Modifier.height(6.dp))
        SettingRow(
            title = "Blocks in plain English",
            subtitle = "Show \"n squared\" instead of n ** 2 on typed blocks",
            checked = plainLabels,
            onToggle = onTogglePlainLabels,
        )
        SettingRow(
            title = "Pytor online",
            subtitle = "Chat reaches the tutor service for expert answers. Off means Codex only.",
            checked = pytorOnline,
            onToggle = onTogglePytorOnline,
        )

        Spacer(Modifier.height(22.dp))
        Text(
            if (confirmReset) "Tap again to wipe all progress" else "Reset progress",
            style = MaterialTheme.typography.labelLarge,
            color = Pal.Coral,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .background(Pal.CoralSoft, RoundedCornerShape(10.dp))
                .clickable {
                    if (confirmReset) {
                        onReset()
                        confirmReset = false
                    } else {
                        confirmReset = true
                    }
                }
                .padding(horizontal = 14.dp, vertical = 10.dp),
        )
        Spacer(Modifier.height(36.dp))
    }
}

@Composable
private fun SettingRow(title: String, subtitle: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = Pal.Text)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Pal.Faint)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Pal.Screen,
                checkedTrackColor = Pal.Lime,
                uncheckedThumbColor = Pal.Faint,
                uncheckedTrackColor = Pal.Chip,
                uncheckedBorderColor = Pal.Edge,
            ),
        )
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
