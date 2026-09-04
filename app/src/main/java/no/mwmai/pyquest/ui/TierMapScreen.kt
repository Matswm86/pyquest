package no.mwmai.pyquest.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import no.mwmai.pyquest.data.Progress
import no.mwmai.pyquest.model.Tier

/**
 * The tier map. Ten rows, hello world at the top and consultancy work at the
 * bottom, each showing how much of that tier sits in Leitner box 3 or higher.
 * Mastery is deliberately not "levels finished", because finishing a level once
 * is not the same as knowing it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TierMapScreen(
    tiers: List<Tier>,
    progress: Progress,
    onStartLevel: (tier: Int, level: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column {
                Text("PyQuest", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(
                    "${progress.xp} XP, ${progress.streakDays} day streak",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
            }
        }

        items(tiers, key = { it.tier }) { tier ->
            val mastery = progress.masteryOf(tier.questions.map { it.id })
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(scheme.surface, RoundedCornerShape(16.dp))
                    .border(1.dp, scheme.surfaceVariant, RoundedCornerShape(16.dp))
                    .padding(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .background(scheme.primary, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "${tier.tier}",
                            color = scheme.onPrimary,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    Spacer(Modifier.size(12.dp))
                    Column {
                        Text(tier.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        if (tier.subtitle.isNotBlank()) {
                            Text(
                                tier.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(progress = { mastery }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(4.dp))
                Text(
                    "${(mastery * 100).toInt()}% mastered, ${tier.questions.size} questions",
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    tier.levels.forEach { level ->
                        val ids = tier.level(level).map { it.id }
                        val levelMastery = progress.masteryOf(ids)
                        Box(
                            modifier = Modifier
                                .background(
                                    if (levelMastery >= 1f) scheme.primary else scheme.surfaceVariant,
                                    RoundedCornerShape(10.dp),
                                )
                                .clickable { onStartLevel(tier.tier, level) }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                        ) {
                            Text(
                                "Level $level",
                                color = if (levelMastery >= 1f) scheme.onPrimary else scheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                }
            }
        }
    }
}
