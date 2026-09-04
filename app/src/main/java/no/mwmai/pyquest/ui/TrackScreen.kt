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
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import no.mwmai.pyquest.data.Progress
import no.mwmai.pyquest.model.Tier
import no.mwmai.pyquest.ui.theme.CodeStyle
import no.mwmai.pyquest.ui.theme.Pal

/**
 * The track: eight tiers from hello world to client work, one card each.
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
    plainLabels: Boolean,
    onTogglePlainLabels: () -> Unit,
    onStartLevel: (tier: Int, level: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentTier = remember(tiers, progress) {
        tiers.firstOrNull { progress.masteryOf(it.questions.map(::questionId)) < 1f }?.tier
            ?: tiers.lastOrNull()?.tier ?: 1
    }
    var openTier by remember { mutableIntStateOf(currentTier) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Pal.Screen)
            .windowInsetsPadding(WindowInsets.systemBars),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { TrackHeader(progress) }
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp),
            ) {
                Text("THE TRACK", style = MaterialTheme.typography.labelMedium, color = Pal.Faint)
                Spacer(Modifier.weight(1f))
                Text(
                    text = if (plainLabels) "blocks: english" else "blocks: code",
                    style = MaterialTheme.typography.labelSmall,
                    color = Pal.Lime,
                    modifier = Modifier
                        .background(Pal.LimeSoft, RoundedCornerShape(7.dp))
                        .clickable(onClick = onTogglePlainLabels)
                        .padding(horizontal = 9.dp, vertical = 5.dp),
                )
            }
        }

        items(tiers, key = { it.tier }) { tier ->
            val ids = tier.questions.map(::questionId)
            val mastery = progress.masteryOf(ids)
            TierCard(
                tier = tier,
                mastery = mastery,
                isCurrent = tier.tier == currentTier,
                isOpen = openTier == tier.tier,
                onToggle = { openTier = if (openTier == tier.tier) 0 else tier.tier },
                onStartLevel = { level -> onStartLevel(tier.tier, level) },
                levelMastery = { level -> progress.masteryOf(tier.level(level).map(::questionId)) },
            )
        }
    }
}

private fun questionId(question: no.mwmai.pyquest.model.Question): String = question.id

@Composable
private fun TrackHeader(progress: Progress) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
    ) {
        Box(
            modifier = Modifier.size(38.dp).background(Pal.Chip, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(">_", style = CodeStyle, color = Pal.Lime)
        }
        Spacer(Modifier.width(11.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("PyQuest", style = MaterialTheme.typography.titleLarge, color = Pal.Text)
            Text(
                "${progress.xp} XP · rank ${progress.rank} · ${progress.streakDays}d streak",
                style = MaterialTheme.typography.bodySmall,
                color = Pal.Faint,
            )
        }
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { progress.dailyProgress },
                color = Pal.Lime,
                trackColor = Pal.Chip,
                strokeWidth = 4.dp,
                modifier = Modifier.size(44.dp),
            )
            Text(
                "${progress.xpToday}",
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
        tier.capstone -> "CAPSTONE"
        done -> "100%"
        isCurrent -> "RESUME"
        else -> "${(mastery * 100).toInt()}%"
    }
    val tagColor = if (tier.capstone) Pal.Violet else if (done || isCurrent) Pal.Lime else Pal.Locked

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isOpen) Pal.CardOpen else Pal.Card, RoundedCornerShape(15.dp))
            .border(
                1.dp,
                if (isOpen) Pal.LimeEdge else Pal.Hairline,
                RoundedCornerShape(15.dp),
            )
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
                Text(
                    tier.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Pal.Text,
                )
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
                        val cleared = levelMastery(level) >= 1f
                        Text(
                            "Level $level",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (cleared) Pal.Screen else Pal.Text,
                            modifier = Modifier
                                .background(
                                    if (cleared) Pal.Lime else Pal.Chip,
                                    RoundedCornerShape(9.dp),
                                )
                                .clickable { onStartLevel(level) }
                                .padding(horizontal = 14.dp, vertical = 9.dp),
                        )
                    }
                }
            }
        }
    }
}

