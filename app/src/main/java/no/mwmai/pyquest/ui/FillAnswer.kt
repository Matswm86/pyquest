package no.mwmai.pyquest.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.dp
import no.mwmai.pyquest.model.Question
import no.mwmai.pyquest.ui.theme.CodeStyle
import no.mwmai.pyquest.ui.theme.Pal

/**
 * Gap-fill inside real code.
 *
 * The code is shown as it will actually run, with holes where the interesting
 * decisions are. Blocks carry a colour for their kind, so a player starts
 * recognising "this hole wants a name" or "this hole wants an expression" before
 * they can articulate why, and the tray always holds more blocks than there are
 * holes so that guessing by elimination stops working.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FillAnswer(
    question: Question,
    placed: List<String?>,
    onPlacedChange: (List<String?>) -> Unit,
    plainLabels: Boolean,
    enabled: Boolean,
    checked: Boolean,
    modifier: Modifier = Modifier,
) {
    val state = rememberSlotDragState()
    val slotCount = question.slotCount
    val used = placed.filterNotNull().toSet()
    val tray = question.blocks.filter { it.id !in used }

    fun place(slot: Int, id: String) {
        val next = placed.toMutableList()
        // A block lives in one place at a time, so empty any slot already holding it.
        next.indices.forEach { if (next[it] == id) next[it] = null }
        next[slot] = id
        onPlacedChange(next)
    }

    fun clear(slot: Int) {
        onPlacedChange(placed.toMutableList().also { it[slot] = null })
    }

    Box(modifier = modifier.onGloballyPositioned { state.parentOrigin = it.positionInWindow() }) {
        Column(modifier = Modifier.fillMaxWidth()) {

            // Code lines never wrap; the panel scrolls sideways instead, so a long
            // line stays readable as code rather than becoming a paragraph.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Pal.Ground, RoundedCornerShape(14.dp))
                    .horizontalScroll(rememberScrollState())
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                question.template.orEmpty().split("\n").forEach { line ->
                    CodeLine(
                        line = line,
                        placed = placed,
                        state = state,
                        question = question,
                        plainLabels = plainLabels,
                        enabled = enabled,
                        checked = checked,
                        onClear = { slot -> clear(slot) },
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("BLOCK TRAY", style = MaterialTheme.typography.labelMedium, color = Pal.Faint)
                Text(
                    "tap to place, tap a gap to clear",
                    style = MaterialTheme.typography.bodySmall,
                    color = Pal.Locked,
                )
            }
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
                        slotCount = slotCount,
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

/** One source line, with its gaps rendered as drop slots in place. */
@Composable
private fun CodeLine(
    line: String,
    placed: List<String?>,
    state: SlotDragState,
    question: Question,
    plainLabels: Boolean,
    enabled: Boolean,
    checked: Boolean,
    onClear: (Int) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        var cursor = 0
        Question.GAP.findAll(line).forEach { match ->
            val literal = line.substring(cursor, match.range.first)
            if (literal.isNotEmpty()) CodeText(literal)
            val slot = match.groupValues[1].toInt()
            DropSlot(
                index = slot,
                state = state,
                filled = placed.getOrNull(slot)?.let(question::block),
                plainLabels = plainLabels,
                enabled = enabled,
                hint = "?",
                onClear = { onClear(slot) },
                verdict = slotVerdict(checked, question, placed, slot),
            )
            cursor = match.range.last + 1
        }
        val tail = line.substring(cursor)
        if (tail.isNotEmpty() || cursor == 0) CodeText(tail)
    }
}

/** After checking, each gap says whether the block in it was the right one. */
private fun slotVerdict(
    checked: Boolean,
    question: Question,
    placed: List<String?>,
    slot: Int,
): SlotVerdict = when {
    !checked -> SlotVerdict.NONE
    placed.getOrNull(slot) == question.answer.getOrNull(slot) -> SlotVerdict.RIGHT
    else -> SlotVerdict.WRONG
}

@Composable
private fun CodeText(text: String) {
    Text(
        text = text,
        style = CodeStyle,
        color = Pal.Text,
        softWrap = false,
        modifier = Modifier.padding(vertical = 7.dp),
    )
}
