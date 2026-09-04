package no.mwmai.pyquest.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import no.mwmai.pyquest.model.Brief
import no.mwmai.pyquest.model.Question
import no.mwmai.pyquest.ui.theme.CodeStyle
import no.mwmai.pyquest.ui.theme.Pal

/**
 * The capstone: wire a client's inference pipeline against their stated budget.
 *
 * This is the one screen where the player is not asked to recall anything. The
 * latency and cost readouts update as components go in, so a wiring that breaches
 * the client's ceiling is visibly wrong before the answer is ever checked, and the
 * lesson lands as a felt trade-off rather than as a fact to memorise.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PipelineAnswer(
    question: Question,
    placed: List<String?>,
    onPlacedChange: (List<String?>) -> Unit,
    plainLabels: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val brief = question.brief ?: return
    val state = rememberSlotDragState()
    val used = placed.filterNotNull().toSet()
    val tray = question.blocks.filter { it.id !in used }

    val chosen = placed.mapNotNull { id -> id?.let(question::block) }
    val totalMs = chosen.sumOf { it.ms }
    val totalCost = chosen.sumOf { it.cost }
    val overTime = totalMs > brief.maxMs
    val overCost = totalCost > brief.maxCost

    fun place(slot: Int, id: String) {
        val next = placed.toMutableList()
        next.indices.forEach { if (next[it] == id) next[it] = null }
        next[slot] = id
        onPlacedChange(next)
    }

    Box(modifier = modifier.onGloballyPositioned { state.parentOrigin = it.positionInWindow() }) {
        Column(modifier = Modifier.fillMaxWidth()) {

            ClientCard(brief)

            Spacer(Modifier.height(16.dp))
            Text(
                "WIRE THE PIPELINE, ${brief.stages} STAGES",
                style = MaterialTheme.typography.labelMedium,
                color = Pal.Faint,
            )
            Spacer(Modifier.height(10.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(brief.stages) { slot ->
                    val block = placed.getOrNull(slot)?.let(question::block)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .background(Pal.ChipDim, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "${slot + 1}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (block == null) Pal.Locked else Pal.Lime,
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        DropSlot(
                            index = slot,
                            state = state,
                            filled = block,
                            plainLabels = plainLabels,
                            enabled = enabled,
                            hint = "stage ${slot + 1}",
                            onClear = {
                                onPlacedChange(placed.toMutableList().also { it[slot] = null })
                            },
                            modifier = Modifier.weight(1f),
                        )
                        if (block != null) {
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "${block.ms} ms",
                                style = CodeStyle,
                                color = Pal.Faint,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Readout(
                    label = "EST. P95",
                    value = "$totalMs ms",
                    limit = "budget ${brief.maxMs} ms",
                    breached = overTime,
                    modifier = Modifier.weight(1f),
                )
                Readout(
                    label = "COST / TICKET",
                    value = money(brief.currency, totalCost),
                    limit = "budget ${money(brief.currency, brief.maxCost)}",
                    breached = overCost,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(18.dp))
            Text(
                "AVAILABLE COMPONENTS",
                style = MaterialTheme.typography.labelMedium,
                color = Pal.Faint,
            )
            Spacer(Modifier.height(10.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                tray.forEach { block ->
                    TrayBlock(
                        block = block,
                        plainLabels = plainLabels,
                        enabled = enabled,
                        state = state,
                        slotCount = brief.stages,
                        onTap = {
                            val slot = placed.indexOfFirst { it == null }
                            if (slot >= 0) place(slot, block.id)
                        },
                        onDroppedOn = { slot -> place(slot, block.id) },
                    )
                }
            }
        }

        DragOverlay(
            state = state,
            block = state.draggingId?.let(question::block),
            plainLabels = plainLabels,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ClientCard(brief: Brief) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Pal.Card, RoundedCornerShape(14.dp))
            .border(1.dp, Pal.Edge, RoundedCornerShape(14.dp))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(Pal.Chip, RoundedCornerShape(11.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    brief.initials,
                    style = MaterialTheme.typography.labelSmall,
                    color = Pal.Lime,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                brief.client,
                style = MaterialTheme.typography.titleMedium,
                color = Pal.Text,
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            brief.quote,
            style = MaterialTheme.typography.bodyMedium,
            color = Pal.Muted,
        )
        if (brief.constraints.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                brief.constraints.forEach { constraint ->
                    Text(
                        text = constraint,
                        style = MaterialTheme.typography.labelSmall,
                        color = Pal.Lime,
                        modifier = Modifier
                            .background(Pal.LimeSoft, RoundedCornerShape(7.dp))
                            .border(1.dp, Pal.LimeEdge, RoundedCornerShape(7.dp))
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun Readout(
    label: String,
    value: String,
    limit: String,
    breached: Boolean,
    modifier: Modifier = Modifier,
) {
    val tint = if (breached) Pal.Coral else Pal.Lime
    Column(
        modifier = modifier
            .background(if (breached) Pal.CoralSoft else Pal.Card, RoundedCornerShape(12.dp))
            .border(1.dp, if (breached) Pal.Coral else Pal.Edge, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Pal.Faint)
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.titleLarge, color = tint)
        Text(limit, style = MaterialTheme.typography.bodySmall, color = Pal.Locked)
    }
}

/** Four decimals, because a per-ticket price is fractions of a cent. */
fun money(currency: String, amount: Double): String {
    val symbol = when (currency.uppercase()) {
        "EUR" -> "€"
        "USD" -> "$"
        "GBP" -> "£"
        "NOK" -> "kr "
        else -> "$currency "
    }
    return symbol + String.format(java.util.Locale.US, "%.4f", amount)
}

