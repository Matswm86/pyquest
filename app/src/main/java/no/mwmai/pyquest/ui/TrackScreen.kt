package no.mwmai.pyquest.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import no.mwmai.pyquest.data.Progress
import no.mwmai.pyquest.model.Tier
import no.mwmai.pyquest.pytor.PytorCoach
import no.mwmai.pyquest.ui.theme.Pal

/**
 * The track: eight tiers from hello world to client work, one card each, with
 * Pytor at the top saying the one thing worth saying right now.
 *
 * Cards are collapsed by default and expand on tap. On a 412 dp wide phone that
 * is the difference between seeing the whole ladder at a glance and scrolling
 * past three tiers of lesson names to find where you are.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TrackScreen(
    tiers: List<Tier>,
    progress: Progress,
    today: String,
    onStartLevel: (tier: Int, level: Int) -> Unit,
    onOpenPytor: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val coach = remember(progress, tiers, today) { PytorCoach.greeting(progress, tiers, today) }
    val currentTier = remember(tiers, progress) {
        PytorCoach.nextLevel(progress, tiers)?.first?.tier ?: tiers.lastOrNull()?.tier ?: 1
    }
    var openTier by remember { mutableIntStateOf(currentTier) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Pal.Screen)
            .windowInsetsPadding(WindowInsets.statusBars),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { TrackHeader(progress, today, onOpenPytor) }
        item {
            PytorSays(
                text = coach.text,
                onClick = {
                    val tier = coach.tier
                    if (tier != null) openTier = tier else onOpenPytor()
                },
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp),
            ) {
                SectionLabel("THE TRACK")
                Spacer(Modifier.weight(1f))
                Text(
                    "${tiers.sumOf { it.questions.size }} questions",
                    style = MaterialTheme.typography.labelSmall,
                    color = Pal.Locked,
                )
            }
        }

        items(tiers, key = { it.tier }) { tier ->
            val ids = tier.questions.map { it.id }
            val mastery = progress.masteryOf(ids)
            TierCard(
                tier = tier,
                mastery = mastery,
                isCurrent = tier.tier == currentTier,
                isOpen = openTier == tier.tier,
                onToggle = { openTier = if (openTier == tier.tier) 0 else tier.tier },
                onStartLevel = { level -> onStartLevel(tier.tier, level) },
                levelMastery = { level -> progress.masteryOf(tier.level(level).map { it.id }) },
            )
        }
    }
}

@Composable
private fun TrackHeader(progress: Progress, today: String, onOpenPytor: () -> Unit) {
    val streak = progress.streakOn(today)
    val atRisk = progress.streakAtRisk(today)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
    ) {
        PytorAvatar(size = 44.dp, modifier = Modifier.clickable(onClick = onOpenPytor))
        Spacer(Modifier.width(11.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("PyQuest", style = MaterialTheme.typography.titleLarge, color = Pal.Text)
            Text(
                "${progress.xp} XP · ${progress.rank} · ${streak}d streak" + if (atRisk) " (at risk)" else "",
                style = MaterialTheme.typography.bodySmall,
                color = if (atRisk) Pal.Coral else Pal.Faint,
            )
        }
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { progress.dailyProgressOn(today) },
                color = Pal.Lime,
                trackColor = Pal.Chip,
                strokeWidth = 4.dp,
                modifier = Modifier.size(44.dp),
            )
            Text(
                "${progress.xpOn(today)}",
                style = MaterialTheme.typography.labelSmall,
                color = Pal.Lime,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TierCard(
    tier: Tier,
    mastery: Float,
    isCurrent: Boolean,
    isOpen: Boolean,
    onToggle: () -> Unit,
    onStartLevel: (Int) -> Unit,
    levelMastery: (Int) -> Float,
) {
    val done = mastery >= 1f
    val chipBg = when {
        done -> Pal.LimeSoft
        isCurrent -> Pal.Lime
        else -> Pal.ChipDim
    }
    val chipFg = when {
        done -> Pal.Lime
        isCurrent -> Pal.Screen
        else -> Pal.Locked
    }
    val tag = when {
        done -> "100%"
        tier.capstone && mastery == 0f -> "CAPSTONE"
        isCurrent -> "RESUME"
        else -> "${(mastery * 100).toInt()}%"
    }
    val tagColor = when {
        done || isCurrent -> Pal.Lime
        tier.capstone -> Pal.Violet
        else -> Pal.Locked
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isOpen) Pal.CardOpen else Pal.Card, RoundedCornerShape(15.dp))
            .border(1.dp, if (isOpen) Pal.LimeEdge else Pal.Hairline, RoundedCornerShape(15.dp))
            .clickable(onClick = onToggle)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(34.dp).background(chipBg, RoundedCornerShape(11.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (done) "✓" else "${tier.tier}",
                    style = MaterialTheme.typography.titleSmall,
                    color = chipFg,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(tier.title, style = MaterialTheme.typography.titleMedium, color = Pal.Text)
                Text(
                    tier.subtitle.ifBlank { "${tier.questions.size} questions" },
                    style = MaterialTheme.typography.bodySmall,
                    color = Pal.Faint,
                )
            }
            Text(tag, style = MaterialTheme.typography.labelSmall, color = tagColor)
        }

        AnimatedVisibility(visible = isOpen) {
            Column {
                if (tier.pytor.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.Top) {
                        PytorAvatar(size = 22.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            tier.pytor,
                            style = MaterialTheme.typography.bodySmall,
                            color = Pal.Muted,
                            fontStyle = FontStyle.Italic,
                        )
                    }
                }
                if (tier.lessons.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        tier.lessons.forEach { lesson ->
                            Text(
                                lesson,
                                style = MaterialTheme.typography.bodySmall,
                                color = Pal.Muted,
                                modifier = Modifier
                                    .background(Pal.ChipDim, RoundedCornerShape(7.dp))
                                    .padding(horizontal = 8.dp, vertical = 5.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    tier.levels.forEach { level ->
                        val levelDone = levelMastery(level)
                        val cleared = levelDone >= 1f
                        val count = tier.level(level).size
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .background(if (cleared) Pal.Lime else Pal.Chip, RoundedCornerShape(9.dp))
                                .clickable { onStartLevel(level) }
                                .padding(horizontal = 13.dp, vertical = 8.dp),
                        ) {
                            Text(
                                "Level $level",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (cleared) Pal.Screen else Pal.Text,
                            )
                            Text(
                                if (cleared) "mastered" else "$count q · ${(levelDone * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (cleared) Pal.Screen.copy(alpha = 0.7f) else Pal.Locked,
                            )
                        }
                    }
                }
            }
        }
    }
}
